package com.gy.tomcat.db;

import java.sql.*;

/**
 * Created by gy on 2018/5/26.
 */
public class MysqlJdbc {
    private static final String NAME = "com.mysql.cj.jdbc.Driver";
    static {
        try {
            Class.forName(NAME);
        }catch (ClassNotFoundException e){
            e.printStackTrace();
        }
    }
    public Connection connectDb(){
        String user = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        String url = "jdbc:mysql://"+System.getenv("DB_ENDPOINT")+"/"+System.getenv("DB_DATABASE")+"?autoReconnect=true&serverTimezone=GMT";
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
}
