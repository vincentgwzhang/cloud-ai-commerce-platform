# AuthService

Stateless JWT authentication microservice for the commerce platform.

All commands below are run from this directory:

```bash
cd AuthService
```

## Stack

- Java 25
- Spring Boot 4.0.6
- Spring Cloud 2025.1.0
- Spring Security 6 (stateless)
- MySQL 8 + Spring Data JPA + HikariCP
- JWT (RS256) via `NimbusJwtEncoder` / `NimbusJwtDecoder`
- springdoc-openapi 3.x

## Prerequisites

- JDK 25
- Maven 3.9+
- MySQL 8 with database `commerce_platform`

Initialize schema and users:

```bash
mysql -u vincent -p commerce_platform < sql/init.sql
```

If you already loaded the original seed file with placeholder BCrypt hashes:

```bash
mysql -u vincent -p commerce_platform < scripts/update-user-passwords.sql
```

Generate RSA keys (also auto-generated on first startup if missing):

```bash
./scripts/generate-rsa-keys.sh
```

## Configuration

Defaults in `src/main/resources/application.yml` support local, Docker, and Kubernetes via environment variables:

| Variable | Default |
|----------|---------|
| `SERVER_PORT` | `8080` |
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `commerce_platform` |
| `DB_USERNAME` | `vincent` |
| `DB_PASSWORD` | `1q2w3e4R` |
| `JWT_EXPIRATION_SECONDS` | `3600` |
| `JWT_PRIVATE_KEY_PATH` | `classpath:keys/private.pem` |
| `JWT_PUBLIC_KEY_PATH` | `classpath:keys/public.pem` |

## Run locally

```bash
mvn spring-boot:run
```

## API

### Login

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"vincent","password":"123456"}'
```

### Validate token

```bash
TOKEN="<accessToken from login>"
curl -s http://localhost:8080/api/v1/auth/validate \
  -H "Authorization: Bearer ${TOKEN}"
```

### Health

```bash
curl -s http://localhost:8080/api/v1/auth/health
```

### Swagger UI

http://localhost:8080/swagger-ui.html

## Docker

```bash
mvn -DskipTests package
docker build -t auth-service:1.0.0 .
docker run --rm -p 8080:8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_USERNAME=vincent \
  -e DB_PASSWORD=1q2w3e4R \
  auth-service:1.0.0
```

## Kubernetes

Manifests under `k8s/`:

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

Populate `auth-service-jwt-keys` with base64-encoded PEM files before production deploy.

## Project layout

```text
src/main/java/com/vincent/authservice/
  controller/
  service/
  repository/
  entity/
  dto/
  security/
  config/
  exception/
```

## Generate BCrypt password hash

```bash
mvn -q exec:java \
  -Dexec.mainClass=com.vincent.authservice.util.PasswordHashGenerator \
  -Dexec.args=your-password
```

## Sample users (after sql/init.sql)

| Username | Password | Role |
|----------|----------|------|
| vincent | 123456 | USER |
| admin | 123456 | ADMIN |
| tester | 123456 | USER |
