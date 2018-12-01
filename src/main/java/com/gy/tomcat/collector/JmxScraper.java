package com.gy.tomcat.collector;

import com.gy.tomcat.httpserver.AddressServlet;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.naming.Context;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by gy on 2018/5/22.
 */
public class JmxScraper {
    public static interface MBeanReceiver {
        void recordBean(
                String domain,
                LinkedHashMap<String, String> beanProperties,
                LinkedList<String> attrKeys,
                String attrName,
                String attrType,
                String attrDescription,
                Object value);

    }
    private final MBeanReceiver receiver;
    private final String jmxUrl;
    private final String username;
    private final String password;
    private final boolean ssl;
    private final List<ObjectName> whitelistObjectNames, blacklistObjectNames;
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
            "([^,=:\\*\\?]+)" + // Name - non-empty, anything but comma, equals, colon, star, or question mark
                    "=" +  // Equals
                    "(" + // Either
                    "\"" + // Quoted
                    "(?:" + // A possibly empty sequence of
                    "[^\\\\\"]*" + // Greedily match anything but backslash or quote
                    "(?:\\\\.)?" + // Greedily see if we can match an escaped sequence
                    ")*" +
                    "\"" +
                    "|" + // Or
                    "[^,=:\"]*" + // Unquoted - can be empty, anything but comma, equals, colon, or quote
                    ")");

    public JmxScraper(String jmxUrl, String username, String password, boolean ssl,
                      List<ObjectName> whitelistObjectNames, List<ObjectName> blacklistObjectNames,
                      MBeanReceiver receiver) {
        this.jmxUrl = jmxUrl;
        this.receiver = receiver;
        this.username = username;
        this.password = password;
        this.ssl = ssl;
        this.whitelistObjectNames = whitelistObjectNames;
        this.blacklistObjectNames = blacklistObjectNames;
    }
    public void doScrape() {
        MBeanServerConnection beanConn;
        JMXConnector jmxc = null;
        LinkedHashMap map = new LinkedHashMap<String,String>();
        Map<String, Object> environment = new HashMap<String, Object>();
        if (username != null && username.length() != 0 && password != null && password.length() != 0) {
            String[] credent = new String[] {username, password};
            environment.put(javax.management.remote.JMXConnector.CREDENTIALS, credent);
        }
        if (ssl) {
            environment.put(Context.SECURITY_PROTOCOL, "ssl");
            SslRMIClientSocketFactory clientSocketFactory = new SslRMIClientSocketFactory();
            environment.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, clientSocketFactory);
            environment.put("com.sun.jndi.rmi.factory.socket", clientSocketFactory);
        }
        try {
            jmxc = JMXConnectorFactory.connect(new JMXServiceURL(jmxUrl),environment);
            beanConn = jmxc.getMBeanServerConnection();
            try {
                Set<ObjectName> mBeanNames = new HashSet<>();
                for (ObjectName name : whitelistObjectNames) {
                    for (ObjectInstance instance : beanConn.queryMBeans(name, null)) {
                        mBeanNames.add(instance.getObjectName());
                    }
                }
                for (ObjectName name : blacklistObjectNames) {
                    for (ObjectInstance instance : beanConn.queryMBeans(name, null)) {
                        mBeanNames.add(instance.getObjectName());
                    }
                }
                for (ObjectName name:mBeanNames){
                    scrapeBean(beanConn, name);
                }
                processBeanValue(
                        "jmx",
                        map,
                        new LinkedList<>(),
                        "monitorstatus",
                        "String",
                        "connection",
                        1
                );
                Long endTime = System.currentTimeMillis();
                Long aqTime = endTime- AddressServlet.startTime;
                processBeanValue(
                        "jmx",
                        map,
                        new LinkedList<>(),
                        "aqTime",
                        "Long",
                        "time of get all data",
                        aqTime
                );
            }catch (Exception e){
                e.printStackTrace();
            }
        }catch (IOException e) {
            processBeanValue(
                    "jmx",
                    map,
                    new LinkedList<>(),
                    "monitorstatus",
                    "String",
                    "connection",
                    0
            );
            e.printStackTrace();
        }
    }

    private void scrapeBean(MBeanServerConnection beanConn, ObjectName mbeanName) {
        MBeanInfo info;
        try {
            info = beanConn.getMBeanInfo(mbeanName);
        } catch (IOException | JMException e) {
            logScrape(mbeanName.toString(), "getMBeanInfo Fail: " + e);
            return;
        }
        MBeanAttributeInfo[] attrInfos = info.getAttributes();
        for (MBeanAttributeInfo attr : attrInfos) {
            if (!attr.isReadable()) {
                logScrape(mbeanName, attr, "not readable");
                continue;
            }
            Object value;
            try {
                value = beanConn.getAttribute(mbeanName, attr.getName());
            } catch (Exception e) {
                logScrape(mbeanName, attr, "Fail: " + e);
                continue;
            }
            logScrape(mbeanName, attr, "process");
            processBeanValue(
                    mbeanName.getDomain(),
                    getKeyPropertyList(mbeanName),
                    new LinkedList<>(),
                    attr.getName(),
                    attr.getType(),
                    attr.getDescription(),
                    value
            );
        }
    }

    public LinkedHashMap<String, String> getKeyPropertyList(ObjectName mbeanName) {
        LinkedHashMap<String, String> output = new LinkedHashMap<>();
        String properties = mbeanName.getKeyPropertyListString();
        Matcher match = PROPERTY_PATTERN.matcher(properties);
        while (match.lookingAt()) {
            output.put(match.group(1), match.group(2));
            properties = properties.substring(match.end());
            if (properties.startsWith(",")) {
                properties = properties.substring(1);
            }
            match.reset(properties);
        }
        return output;
    }


    /**
     * Recursive function for exporting the values of an mBean.
     * JMX is a very open technology, without any prescribed way of declaring mBeans
     * so this function tries to do a best-effort pass of getting the values/names
     * out in a way it can be processed elsewhere easily.
     */
    private void processBeanValue(
            String domain,
            LinkedHashMap<String, String> beanProperties,
            LinkedList<String> attrKeys,
            String attrName,
            String attrType,
            String attrDescription,
            Object value) {
        if (value == null) {
            logScrape(domain + beanProperties + attrName, "null");
        } else if (value instanceof Number || value instanceof String || value instanceof Boolean) {
            if ("Uptime".equals(attrName)) {
                logScrape(domain + beanProperties + attrName, value.toString());
                Integer nh = 10;
                Object valu = (Long) value / nh.doubleValue();
                this.receiver.recordBean(domain, beanProperties, attrKeys, attrName, attrType, attrDescription, valu);
            } else if ("requestCount".equals(attrName) || "errorCount".equals(attrName) || ("processingTime".equals(attrName)
                    && "GlobalRequestProcessor".equals(beanProperties.get("type"))) || "currentThreadCount".equals(attrName)
                    || "currentThreadBusy".equals(attrName) || "maxThreads".equals(attrName) || "bytesReceived".equals(attrName)
                    || "bytesSent".equals(attrName)) {
                logScrape(domain + beanProperties + attrName, value.toString());
                String name = beanProperties.get("name");
                String newName = name.replaceAll("\"", "");
                beanProperties.replace("name", name, newName);
                this.receiver.recordBean(domain, beanProperties, attrKeys, attrName, attrType, attrDescription, value);
            } else if ("ProcessCpuLoad".equals(attrName) || "monitorstatus".equals(attrName) || "used".equals(attrName)
                    || "committed".equals(attrName) || "DaemonThreadCount".equals(attrName) || "sessionCounter".equals(attrName)
                    || "activeSessions".equals(attrName) || "rejectedSessions".equals(attrName) || "maxActiveSessions".equals(attrName)
                    || "maxActive".equals(attrName)) {
                logScrape(domain + beanProperties + attrName, value.toString());
                this.receiver.recordBean(domain, beanProperties, attrKeys, attrName, attrType, attrDescription, value);
            }
        } else if (value instanceof CompositeData) {
            logScrape(domain + beanProperties + attrName, "compositedata");
            CompositeData composite = (CompositeData) value;
            CompositeType type = composite.getCompositeType();
            attrKeys = new LinkedList<>(attrKeys);
            attrKeys.add(attrName);
            if ("HeapMemoryUsage".equals(attrName) || "NonHeapMemoryUsage".equals(attrName)) {
                for (String key : type.keySet()) {
                    String typ = type.getType(key).getTypeName();
                    Long nx = (Long) composite.get(key);
                    Integer nh = 1024;
                    Double mn = nx / nh.doubleValue();
                    processBeanValue(
                            domain,
                            beanProperties,
                            attrKeys,
                            key,
                            typ,
                            type.getDescription(),
                            mn
                    );
                }
            }
        } else if (value.getClass().isArray()) {
            logScrape(domain, "arrays are supported");
        } else {
            logScrape(domain + beanProperties + attrType, "is not exported");
        }
    }



    /**
     * For debugging.
     */
    private static void logScrape(ObjectName mbeanName, Set<String> names, String msg) {
        logScrape(mbeanName + "_" + names, msg);
    }
    private static void logScrape(ObjectName mbeanName, MBeanAttributeInfo attr, String msg) {
        logScrape(mbeanName + "'_'" + attr.getName(), msg);
    }
    private static void logScrape(String name, String msg) {
//        logger.log(Level.FINE, "scrape: '" + name + "': " + msg);
    }

}
