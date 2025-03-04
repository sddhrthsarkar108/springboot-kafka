FROM amazoncorretto:21.0.2-al2023-headless

WORKDIR /app

COPY build/libs/mod2.jar /app/mod2.jar

EXPOSE 8084

CMD ["java", "-jar", "mod2.jar"]
