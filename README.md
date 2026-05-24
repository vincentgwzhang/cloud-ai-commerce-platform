# Cloud AI Commerce Platform

Monorepo for commerce platform microservices. Each service lives in its own top-level folder.

## Services

| Folder | Description |
|--------|-------------|
| [AuthService](AuthService/) | Stateless JWT authentication (login, refresh, validate, health) |

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
mvn -pl AuthService compile
```

In IntelliJ: **Maven** tool window → **Reload All Maven Projects** (or right-click root `pom.xml` → **Maven** → **Reload project**).

## Postman

Import at repository root:

| File | Purpose |
|------|---------|
| [postman/cloud-ai-commerce-platform.postman_collection.json](postman/cloud-ai-commerce-platform.postman_collection.json) | Collection with **Auth Service** folder and REST endpoints |
| [postman/cloud-ai-commerce-platform.local.postman_environment.json](postman/cloud-ai-commerce-platform.local.postman_environment.json) | Optional local variables (`authServiceBaseUrl`, `accessToken`, `refreshToken`) |

1. Postman → **Import** → select both files from `postman/` (or collection only).
2. Select environment **Cloud AI Commerce Platform - Local**.
3. Start **AuthService**, run **Auth Service → Login** — saves `accessToken` (Bearer on all requests) and `refreshToken`.
4. When the JWT expires (~1 h), run **Auth Service → Refresh Token** — updates both tokens (rotation; old refresh token cannot be reused).
5. **Validate Token** checks the current `accessToken`. Details: [AuthService README — refresh flow](AuthService/README.md#refresh-token-flow).
