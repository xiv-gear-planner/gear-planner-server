FROM openjdk:20

EXPOSE 8080

WORKDIR /app

COPY target/gear-planner-server-1.0-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]