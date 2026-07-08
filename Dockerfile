FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates unzip \
 && curl -sSL -o /tmp/nr.zip https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-java.zip \
 && unzip -q /tmp/nr.zip -d /opt && rm /tmp/nr.zip \
 && apt-get purge -y unzip && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/orders.jar .
EXPOSE 8007
ENV NEW_RELIC_APP_NAME=roboshop-orders
ENV NEW_RELIC_DISTRIBUTED_TRACING_ENABLED=true
ENV NEW_RELIC_LOG_FILE_NAME=STDOUT
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/newrelic/newrelic.jar"
CMD ["java", "-jar", "orders.jar"]

#