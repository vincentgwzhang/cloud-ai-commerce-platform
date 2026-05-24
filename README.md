# Cloud AI Commerce Platform

Monorepo for commerce platform microservices. Each service lives in its own top-level folder.

## Services

| Folder | Description |
|--------|-------------|
| [AuthService](AuthService/) | Stateless JWT authentication (login, refresh, validate, health) |
| [ProductService](ProductService/) | Product catalog with Redis cache (JWT resource server) |

Additional services will be added alongside `AuthService/` as separate Maven projects.

## Repository layout

```text
cloud-ai-commerce-platform/
├── README.md           # This file
├── AuthService/        # Auth microservice (Spring Boot)
│   ├── pom.xml
│   ├── src/
│   ├── sql/
│   ├── scripts/
│   ├── k8s/
│   └── Dockerfile
└── <OtherService>/     # Future microservices
```

Open the **repository root** in IntelliJ / VS Code / Cursor. The root `pom.xml` aggregates microservice modules so the IDE resolves Maven dependencies correctly.

After cloning or pulling:

```bash
# From repository root
mvn -pl AuthService,ProductService compile
```

In IntelliJ: **Maven** tool window → **Reload All Maven Projects** (or right-click root `pom.xml` → **Maven** → **Reload project**).

## Local development (IntelliJ) — recommended

Run **both services on the host** with the same JWT key pair and `JWT_ISSUER=auth-service`.

### One-time setup

```bash
chmod +x scripts/local-dev-setup.sh
./scripts/local-dev-setup.sh
```

MySQL: `AuthService/sql/init.sql` on database `commerce_platform`. Redis on `localhost:6379`. Product rows: Flyway runs when ProductService starts.

### Start order in IntelliJ

Use run configurations from `.run/` (imported automatically in IntelliJ):

| Order | Configuration | URL |
|-------|----------------|-----|
| 1 | **AuthService [local]** | http://localhost:8080 |
| 2 | **ProductService [local]** | http://localhost:8081 |

Profile `local`, working directories `AuthService/` and `ProductService/`. ProductService reads `../AuthService/data/keys/public.pem` — no Minikube required.

### Verify

```bash
# Login (AuthService)
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"vincent","password":"123456"}'

# List products (paste accessToken)
curl -s http://localhost:8081/api/v1/products -H "Authorization: Bearer <accessToken>"
```

### Other deploy paths (unchanged)

| Goal | Command |
|------|---------|
| Auth in Docker | `AuthService/scripts/docker-run.sh` (`SPRING_PROFILES_ACTIVE=docker`) |
| Auth on Minikube | `AuthService/scripts/minikube-deploy.sh` |
| Product in Docker | `ProductService/scripts/docker-run.sh` |

Do not mix tokens: login URL and ProductService must use the **same** Auth deployment and key pair.

## Postman

Import at repository root:

| File | Purpose |
|------|---------|
| [postman/cloud-ai-commerce-platform.postman_collection.json](postman/cloud-ai-commerce-platform.postman_collection.json) | Collection with **Auth Service** folder and REST endpoints |
| [postman/cloud-ai-commerce-platform.local.postman_environment.json](postman/cloud-ai-commerce-platform.local.postman_environment.json) | Optional local variables (`authServiceBaseUrl`, `productServiceBaseUrl`, `accessToken`, `refreshToken`) |

1. Postman → **Import** → select both files from `postman/` (or collection only).
2. Select environment **Cloud AI Commerce Platform - Local**.
3. Start **AuthService**, run **Auth Service → Login** — saves `accessToken` (Bearer on all requests) and `refreshToken`.
4. Start **ProductService** (8081), run **Product Service → List Products** (uses same JWT).
5. When the JWT expires (~1 h), run **Auth Service → Refresh Token** — updates both tokens.
