FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /source
COPY blindbox-springboot/pom.xml .
COPY blindbox-springboot/.mvn .mvn
COPY blindbox-springboot/src src
RUN mvn -s .mvn/settings.xml -B package -DskipTests
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/uploads && chown -R 10001:10001 /app
COPY --from=build /source/target/blindbox-springboot-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
