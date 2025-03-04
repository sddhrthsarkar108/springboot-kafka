FROM amazoncorretto:21.0.2-al2023-headless

WORKDIR /app

COPY build/libs/producer.jar /app/producer.jar

EXPOSE 8081

CMD ["java", "-jar", "producer.jar"]
