FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY agent-common/pom.xml agent-common/pom.xml
COPY agent-core/pom.xml agent-core/pom.xml
COPY agent-code-review/pom.xml agent-code-review/pom.xml
COPY agent-server/pom.xml agent-server/pom.xml

RUN mvn -q dependency:go-offline -B

COPY agent-common/src agent-common/src
COPY agent-core/src agent-core/src
COPY agent-code-review/src agent-code-review/src
COPY agent-server/src agent-server/src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /app/agent-server/target/agent-server-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
