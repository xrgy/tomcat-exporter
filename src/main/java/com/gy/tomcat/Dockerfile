FROM docker-registry:443/h3cloud-framework/base-project-library:2.2.1-RELEASE
USER root
RUN mkdir /root/tomcat-exporter
COPY tomcat-exporter /root/tomcat-exporter/
COPY config.yaml /root/tomcat-exporter
EXPOSE 9105
ENTRYPOINT["java"]
CMD ["-Xms512m","-Xmx512m","-jar","/root/tomcat-exporter/tomcat-exporter.jar"]