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

Generate RSA keys into `data/keys/` (or set `JWT_KEYS_DIR`):

```bash
./scripts/generate-rsa-keys.sh
```

On first startup, keys are also auto-generated when using default `file:./data/keys/*.pem` paths.

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
| `JWT_PRIVATE_KEY_PATH` | `file:./data/keys/private.pem` |
| `JWT_PUBLIC_KEY_PATH` | `file:./data/keys/public.pem` |
| `JWT_KEYS_DIR` | (script only) output directory for `generate-rsa-keys.sh` |

## Run locally

```bash
mvn spring-boot:run
```

## Tests & coverage

Tests use an in-memory H2 database (`test` profile) and classpath RSA keys under `src/test/resources/keys/`.

```bash
mvn test verify
```

JaCoCo enforces **≥ 85% line coverage** on non-excluded code (DTOs, main class, and `PasswordHashGenerator` are excluded). Report:

```text
target/site/jacoco/index.html
```

Current coverage is ~98% lines after the test suite.

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

MySQL on the **Ubuntu host** + app in **Docker** → do **not** use `DB_HOST=localhost` inside the container.  
`localhost` in the container means the container itself, not your machine → `Connection refused`.

### Build

```bash
mvn -DskipTests package
./scripts/generate-rsa-keys.sh   # ensures data/keys/*.pem exist for the image
docker build -t auth-service:1.0 .
```

### Run (recommended)

```bash
chmod +x scripts/docker-run.sh
./scripts/docker-run.sh
```

Equivalent manual command:

```bash
docker run --rm -p 8080:8080 \
  --add-host=host.docker.internal:host-gateway \
  -e DB_HOST=host.docker.internal \
  -e DB_USERNAME=vincent \
  -e DB_PASSWORD=1q2w3e4R \
  auth-service:1.0
```

Optional: `-e SPRING_PROFILES_ACTIVE=docker` uses `application-docker.yml` defaults.

### If MySQL still unreachable

1. On the host, confirm MySQL listens beyond 127.0.0.1:
   ```bash
   ss -tlnp | grep 3306
   ```
   If only `127.0.0.1:3306`, set `bind-address = 0.0.0.0` in MySQL config and restart MySQL.

2. **MySQL user must allow Docker IPs** (not only `localhost`). If you see:
   `Host '172.17.0.x' is not allowed to connect to this MySQL server`
   run on the host:
   ```bash
   sudo mysql < scripts/grant-mysql-docker-access.sql
   ```
   Check: `vincent` should have a row with `host` = `%` (see `SELECT user, host FROM mysql.user WHERE user='vincent';`).

3. Test from a throwaway container:
   ```bash
   docker run --rm --add-host=host.docker.internal:host-gateway mysql:8 \
     mysql -h host.docker.internal -u vincent -p1q2w3e4R -e "SELECT 1"
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
