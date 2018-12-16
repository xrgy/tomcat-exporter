package com.gy.tomcat.httpserver;


import com.gy.tomcat.db.MysqlJdbc;
import com.gy.tomcat.model.DbData;
import com.gy.tomcat.model.GetInfo;
import com.sun.jersey.api.container.filter.GZIPContentEncodingFilter;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Created by gy on 2018/5/22.
 */
public class WebServer {
    public static void main(String[] args)throws Exception{
        InetSocketAddress socket = new InetSocketAddress("0.0.0.0",9105);
        Server server = new Server(socket);
        ServletHolder servletHolder = new ServletHolder(ServletContainer.class);
        servletHolder.setInitParameter("com.sun.jersey.config.property.resourceConfigClass", "com.sun.jersey.api.core.PackagesResourceConfig");
        servletHolder.setInitParameter("com.sun.jersey.config.property.packages", "com.gy.tomcat.httpserver");
        servletHolder.setInitParameter(ResourceConfig.PROPERTY_CONTAINER_RESPONSE_FILTERS, GZIPContentEncodingFilter.class.getName());
        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        handler.addServlet(new ServletHolder(new AddressServlet()),"/tomcat");
        handler.addServlet(servletHolder,"/api/*");
        server.setHandler(handler);
        server.start();
    }

    public static DbData getDbData(String target){
        String sql1 = String.format("select ip from tbl_monitor_record where uuid='%s'",target);
        String sql2 = String.format("select monitor_info from tbl_monitor_record where uuid=" +
                "'%s'",target);
        MysqlJdbc mysqlJdbc = new MysqlJdbc();
        Connection conn = null;
        try {
            conn = mysqlJdbc.connectDb();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Statement stmt = null;
        ResultSet rs =null;
        DbData dbData = new DbData();
        GetInfo getInfo;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql1);
            while (rs.next()){
                dbData.setIp(rs.getString("ip"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }finally {
            mysqlJdbc.closeRs(rs);
            mysqlJdbc.closeStmt(stmt);
        }
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql2);
            while (rs.next()){
                String string = rs.getString(1);
                getInfo = new ObjectMapper().readValue(string,GetInfo.class);
                dbData.setPort(getInfo.getPort());
                dbData.setUserName(getInfo.getUserName());
                dbData.setPassword(getInfo.getPassword());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            mysqlJdbc.closeRs(rs);
            mysqlJdbc.closeStmt(stmt);
            mysqlJdbc.closeConn(conn);
        }
        return dbData;
    }
}
