FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY GatewayService/target/gateway-service-*.jar app.jar
COPY devops/data/keys/public.pem /app/keys/public.pem

ENV SERVER_PORT=8088 \
    JWT_PUBLIC_KEY_PATH=file:/app/keys/public.pem \
    SPRING_PROFILES_ACTIVE=docker

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]
