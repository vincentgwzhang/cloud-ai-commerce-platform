FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --chown=spring:spring target/auth-service-*.jar app.jar
COPY --chown=spring:spring data/keys/ /app/keys/

ENV SERVER_PORT=8080 \
    JWT_PRIVATE_KEY_PATH=file:/app/keys/private.pem \
    JWT_PUBLIC_KEY_PATH=file:/app/keys/public.pem

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
