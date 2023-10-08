FROM oracle/jdk:21

EXPOSE 8080

WORKDIR /app

COPY target/gear-planner-server-1.0-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]