FROM openjdk:21

EXPOSE 8080

WORKDIR /app

COPY target/gear-planner-server-1.0-SNAPSHOT.jar /app/app.jar

HEALTHCHECK CMD curl --fail http://localhost:8080

ENTRYPOINT ["java", "-jar", "app.jar"]