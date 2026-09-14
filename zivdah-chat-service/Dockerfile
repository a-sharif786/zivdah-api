# Use JDK 21 or whichever version you are using
FROM eclipse-temurin:21-jdk-alpine
VOLUME /tmp
# Copy the jar file from your target folder
COPY target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]