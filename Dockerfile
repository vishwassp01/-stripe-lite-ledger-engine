FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the pre-built JAR file compiled on the host machine
COPY target/ledger-engine-0.0.1-SNAPSHOT.jar app.jar

# Expose the application port
EXPOSE 8080

# Run the application with the "docker" profile active
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
