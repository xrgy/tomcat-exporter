package com.gy.tomcat.db;

import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.Response;

import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Created by gy on 2018/5/26.
 */
public class MysqlJdbc {
    private static final String IP="47.94.157.199";

    private static final String ETCD_PORT="2379";
    private static final String PATH_SERVICE_INFO="v2/keys/registry/services/specs/default/";
    private static final String HTTP="http://";



    private static final String NAME = "com.mysql.cj.jdbc.Driver";
    static {
        try {
            Class.forName(NAME);
        }catch (ClassNotFoundException e){
            e.printStackTrace();
        }
    }
    public Connection connectDb() throws Exception {
        String user = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        String dbEndpoint =System.getenv("DB_ENDPOINT");

        String []str =dbEndpoint.split(":");
        String dbName =System.getenv("DB_DATABASE");
        String url = "jdbc:mysql://"+dbEndpoint+"/"+dbName+"?autoReconnect=true&serverTimezone=GMT";
//        String ip = getClusterIpByServiceName(str[0]);
//        System.out.println(ip);
//        String url = "jdbc:mysql://"+ip+":"+str[1]+"/"+dbName+"?autoReconnect=true&serverTimezone=GMT";
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url,user,password);
        }catch (SQLException e){
            e.printStackTrace();
            System.exit(0);
        }
        return conn;
    }
    public void closeConn(Connection conn){
        try {
            if (null != conn){
                conn.close();
            }
        }catch (SQLException e){
            e.printStackTrace();
        }
    }
    public void closeStmt(Statement stmt){
        try {
            if (null != stmt){
                stmt.close();
            }
        }catch (SQLException e){
            e.printStackTrace();
        }
    }
    public void closeRs(ResultSet rs){
        try {
            if (null != rs){
                rs.close();
            }
        }catch (SQLException e){
            e.printStackTrace();
        }
    }
    public static String getClusterIpByServiceName(String serviceName) throws Exception {
        String prefix = HTTP+IP + ":" + ETCD_PORT + "/";
        ObjectMapper objectMapper = new ObjectMapper();
        HttpClient client = new  HttpClient();
        client.setFollowRedirects(false);
        client.start();
        ContentResponse responset = client.newRequest(prefix+PATH_SERVICE_INFO+serviceName)
                .method(HttpMethod.GET)
                .agent("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:17.0) Gecko/20100101 Firefox/17.0")
                .send();
        String response = responset.getContentAsString();
//        RestTemplate rest = new RestTemplate();
//        String response = rest.getForObject(prefix+PATH_SERVICE_INFO+serviceName,String.class);
        Map<String,Object> resmap = objectMapper.readValue(response,HashMap.class);
        Map<String,String> nodeMap = (Map<String, String>) resmap.get("node");
        String cont =  nodeMap.get("value");
        Map<String,Object> contMap = objectMapper.readValue(cont,HashMap.class);
        Map<String,String> specMap = (Map<String, String>) contMap.get("spec");
        String clusterIP = specMap.get("clusterIP");
        return clusterIP;
    }
}
