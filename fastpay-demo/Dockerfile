# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms128m -Xmx256m"
COPY --from=build /build/target/*.jar app.jar
EXPOSE 7002
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
