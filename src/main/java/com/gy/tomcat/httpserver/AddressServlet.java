package com.gy.tomcat.httpserver;

import com.gy.tomcat.collector.JmxCollector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by gy on 2018/5/22.
 */
public class AddressServlet extends HttpServlet{

    public static Long startTime;
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        startTime = System.currentTimeMillis();
        JmxCollector collector = new JmxCollector(req.getParameter("target"));
        CollectorRegistry reg = new CollectorRegistry();
        reg.register(collector);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(TextFormat.CONTENT_TYPE_004);
        try(PrintWriter writer = resp.getWriter()) {
            TextFormat.write004(writer, reg.metricFamilySamples());
            writer.flush();
        }finally {
            reg.unregister(collector);
        }

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req,resp);
    }
}
