FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8443 8080
ENTRYPOINT ["java", "-jar", "app.jar"]