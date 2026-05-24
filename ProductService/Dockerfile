FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --chown=spring:spring target/product-service-*.jar app.jar
COPY --chown=spring:spring data/keys/ /app/keys/

ENV SERVER_PORT=8081 \
    JWT_PUBLIC_KEY_PATH=file:/app/keys/public.pem \
    SPRING_PROFILES_ACTIVE=docker

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
