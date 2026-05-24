# ProductService

Product catalog microservice for the commerce platform POC: MySQL + Redis cache-aside, JWT protection aligned with **AuthService**.

Default port: **8081** (AuthService uses 8080).

```bash
cd ProductService
```

**Prerequisites:** JDK 25, Maven 3.9+, MySQL 8 (`commerce_platform`), Redis 7, AuthService RSA **public** key.

---

## 1. One-time setup

From **repository root**:

```bash
./scripts/local-dev-setup.sh
```

Or manually:

```bash
mysql -u vincent -p commerce_platform < ../AuthService/sql/init.sql
cd ../AuthService && ./scripts/generate-rsa-keys.sh
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

Flyway creates `products` and seed data on first startup (`V1__init_product.sql`).

**IntelliJ:** profile `local` uses `ProductService/data/keys/public.pem` (created by `local-dev-setup.sh`). Run config **ProductService [local]** sets working dir to `ProductService/`.

---

## 2. Run locally (IntelliJ / Maven)

**Start AuthService first** (http://localhost:8080), then:

- IntelliJ: **`ProductService [local]`** from `.run/` at repo root, or
- Terminal: `cd ProductService && mvn spring-boot:run` (default profile `local`)

http://localhost:8081

---

## 3. Security (same mechanism as AuthService)

- **OAuth2 Resource Server** with RS256 JWT
- Validates `Authorization: Bearer <accessToken>` using AuthService **public.pem**
- Issuer must match `JWT_ISSUER` (default `auth-service`)

Obtain a token from AuthService:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"vincent","password":"123456"}' | jq -r .accessToken)

curl -s http://localhost:8081/api/v1/products \
  -H "Authorization: Bearer $TOKEN" | jq
```

Public routes: `/api/v1/products/health`, Swagger, `/actuator/health`, `/actuator/prometheus`.

### AuthService on Minikube + ProductService in IntelliJ (optional; 401 troubleshooting)

ProductService verifies JWTs with the **same RSA public key** that signed the token. Minikube AuthService uses keys from Secret `auth-service-jwt-keys` (created from `AuthService/data/keys` at deploy time).

1. **Sync public key** (pick one):
   ```bash
   ./scripts/sync-jwt-public-key.sh
   # or, if Minikube keys may differ from your working tree:
   ./scripts/sync-jwt-public-key-from-minikube.sh
   ```
2. **IntelliJ run config** (optional overrides):
   - `JWT_PUBLIC_KEY_PATH=file:/absolute/path/to/AuthService/data/keys/public.pem`
   - `JWT_ISSUER=auth-service` (must match Minikube ConfigMap)
3. **Get a new token from Minikube** (old tokens signed with a previous key pair will always 401):
   ```bash
   AUTH_URL=$(minikube service auth-service --url | head -1)
   curl -s -X POST "${AUTH_URL}/api/v1/auth/login" \
     -H 'Content-Type: application/json' \
     -d '{"username":"vincent","password":"123456"}' | jq .
   ```
4. Call ProductService on **8081** with `Authorization: Bearer <accessToken>`.

| Symptom | Cause |
|---------|--------|
| 401 on `/api/v1/products` | Missing/wrong `public.pem`, or token from another key pair |
| App fails at startup | `JWT_PUBLIC_KEY_PATH` points to a missing file |
| 401 after redeploy | Login again; Minikube may have new JWT keys |

---

## 4. API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/products/health` | No | Health |
| `GET` | `/api/v1/products` | JWT | List active products |
| `GET` | `/api/v1/products/hot` | JWT | Hot products (IDs 1–3) |
| `GET` | `/api/v1/products/{id}` | JWT | Detail (Redis cache-aside + SETNX lock) |

Success envelope:

```json
{
  "success": true,
  "data": { },
  "timestamp": "2026-05-24T12:00:00Z"
}
```

Errors match AuthService shape (`timestamp`, `status`, `error`, `message`, `path`).

---

## 5. Docker

```bash
chmod +x scripts/docker-run.sh
./scripts/docker-run.sh
```

See `docker-compose.snippet.yml` for Redis + product-service.

---

## 6. Tests

```bash
mvn test verify
```

Integration tests use Testcontainers Redis (`disabledWithoutDocker = true` if Docker is unavailable).

JaCoCo report: `target/site/jacoco/index.html`

---

## 7. Configuration

| Env | Default | Description |
|-----|---------|-------------|
| `SERVER_PORT` | `8081` | HTTP port |
| `JWT_ISSUER` | `auth-service` | Must match token issuer |
| `JWT_PUBLIC_KEY_PATH` | `file:./data/keys/public.pem` | RS256 public key |
| `REDIS_HOST` | `localhost` | Redis host |
| `PRODUCT_CACHE_TTL` | `10m` | Cache TTL |
| `PRODUCT_HOT_IDS` | `1,2,3` | Hot preload IDs |

Profiles: `local`, `docker`, `k8s` — see `application-*.yml`.
