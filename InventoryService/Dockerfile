FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY InventoryService/target/inventory-service-*.jar app.jar
COPY devops/data/keys/public.pem /app/keys/public.pem

ENV SERVER_PORT=8082 \
    JWT_PUBLIC_KEY_PATH=file:/app/keys/public.pem \
    SPRING_PROFILES_ACTIVE=docker

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
