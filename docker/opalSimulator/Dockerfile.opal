FROM openjdk:8-jdk-alpine

RUN apk update && \
    apk add maven

COPY . /app/
WORKDIR /app
EXPOSE 8081

RUN mvn compile && \
    mvn clean package
