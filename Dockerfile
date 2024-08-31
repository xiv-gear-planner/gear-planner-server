FROM openjdk:21

EXPOSE 8080

WORKDIR /app

COPY target/gear-planner-server-1.0-SNAPSHOT.jar /app/app.jar

HEALTHCHECK CMD curl --fail http://localhost:8080/healthcheck

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MinHeapFreeRatio=15", "-XX:MaxHeapFreeRatio=30", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]