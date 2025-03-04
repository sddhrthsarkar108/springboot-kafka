FROM amazoncorretto:21.0.2-al2023-headless

WORKDIR /app

COPY build/libs/consumer.jar /app/consumer.jar

EXPOSE 8084

CMD ["java", "-jar", "consumer.jar"]
