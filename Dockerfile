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
ENTRYPOINT ["java"]
CMD ["-Xms512m","-Xmx512m","-jar","/tomcat-exporter.jar"]