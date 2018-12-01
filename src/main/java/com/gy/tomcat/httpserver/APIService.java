package com.gy.tomcat.httpserver;

import com.gy.tomcat.collector.JmxCollector;
import com.sun.jersey.api.client.ClientResponse;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnector;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.naming.Context.*;

/**
 * Created by gy on 2018/5/22.
 */
@Path("v1")
public class APIService {
    private List<ObjectName> whitelistObjectNamees,blacklistObjectNamees;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HashMap getMonitorFromRequest(String req) throws IOException{
        HashMap monitorMap = new HashMap(4);
        JsonNode jsonNode = objectMapper.readValue(req,JsonNode.class);
        JsonNode info =  jsonNode.get("monitor_info");
        if (null == info){
            //logger
        }else {
            monitorMap = objectMapper.convertValue(info,HashMap.class);
        }
        return monitorMap;
    }

    @POST
    @Path("/tomcat/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response tomcatAccess(String req) throws Exception{
        HashMap<String,String> monitorInfoMap =getMonitorFromRequest(req);
        String[] keys = new String[]{"ip","port","username","password","ssl","authentication"};
        Boolean illegal = Arrays.stream(keys).map(monitorInfoMap::get).anyMatch(x->x == null);
        if (illegal){
            return Response.status(ClientResponse.Status.BAD_REQUEST).type("text/plain").build();
        }
        String ip = monitorInfoMap.get("ip");
        String port = monitorInfoMap.get("port");
        String ssl = monitorInfoMap.get("ssl");
        String auth = monitorInfoMap.get("authentication");
        Boolean access;
        String message ="";
        MyObj myObj = new MyObj();
        Result result = new Result();
        ObjectWriter writer = new ObjectMapper().writer();
        int timeout = 5000;
        boolean status = InetAddress.getByName(ip).isReachable(timeout);
        if (!status){
            access = false;
            result.setAccessible(access);
            result.setMessage("ip does not exist");
            myObj.setResult(result);
            String s =  writer.writeValueAsString(myObj);
            return Response.ok(s).build();
        }
        String jmxUrl = "service:jmx:rmi:///jndi/rmi://"+ip+":"+port+"/jmxrmi";
        Map<String,Object> environment = new HashMap<>(100);
        if (Boolean.valueOf(auth)){
            String username = monitorInfoMap.get("username");
            String password = monitorInfoMap.get("password");
            String[] credentials = new String[]{username,password};
            environment.put(JMXConnector.CREDENTIALS,credentials);
        }
        if (Boolean.valueOf(ssl)){
            environment.put(SECURITY_PROTOCOL,"ssl");
            SslRMIClientSocketFactory clientSocketFactory = new SslRMIClientSocketFactory();
            environment.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE,clientSocketFactory);
            environment.put("com.sun.jndi.rmi.factory.socket",clientSocketFactory);
        }
        JMXServiceURL jmxServiceURL;
        jmxServiceURL = new JMXServiceURL(jmxUrl);
        try{
            JMXConnector jmxConnector = JMXConnectorFactory.connect(jmxServiceURL,environment);
            jmxConnector.getMBeanServerConnection();
            access = true;
        }catch (Exception e){
            access = false;
            message = e.getMessage();
        }
        result.setAccessible(access);
        result.setMessage(message);
        myObj.setResult(result);
        String s = writer.writeValueAsString(myObj);
        return Response.ok(s).build();
    }

    @XmlRootElement
    public static class MyObj{
        private Result result;

        public Result getResult() {
            return result;
        }

        public void setResult(Result result) {
            this.result = result;
        }
    }

    public static class Result{
        private boolean accessible;
        private String message;

        public void setAccessible(boolean accessiable) {
            this.accessible = accessiable;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @XmlElement(type = boolean.class)
        public boolean isAccessible(){
            return accessible;
        }


    }


}
