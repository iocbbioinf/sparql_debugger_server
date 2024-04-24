FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp
COPY build/libs/debugger-0.0.1-SNAPSHOT.jar debugger.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/debugger.jar"]
USER 1000
