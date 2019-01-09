FROM centos
USER root
#RUN mkdir /root/tomcat-exporter
ADD jdk-8u191-linux-x64.tar.gz /usr/local/

ENV JAVA_HOME /usr/local/jdk1.8.0_191
ENV CLASSPATH $JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar
ENV PATH $PATH:$JAVA_HOME/bin
COPY tomcat-exporter.jar /tomcat-exporter.jar
COPY config.yaml /config.yaml
COPY lib /lib
EXPOSE 9105
EXPOSE 30015

ENTRYPOINT ["java",\
            "-Djava.rmi.server.hostname=47.94.157.199",\
            "-Dcom.sun.management.jmxremote=true",\
            "-Dcom.sun.management.jmxremote.port=30015",\
            "-Dcom.sun.management.jmxremote.rmi.port=30015",\
            "-Dcom.sun.management.jmxremote.ssl=false",\
            "-Dcom.sun.management.jmxremote.authenticate=false",\
            "-Dcom.sun.management.jmxremote.local.only=false",\
            "-Xms512m","-Xmx512m","-jar","/tomcat-exporter.jar"]

#ENTRYPOINT ["java"]
#CMD ["-Xms512m","-Xmx512m","-jar","/tomcat-exporter.jar"]