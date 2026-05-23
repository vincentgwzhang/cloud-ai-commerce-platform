# Cloud AI Commerce Platform

Monorepo for commerce platform microservices. Each service lives in its own top-level folder.

## Services

| Folder | Description |
|--------|-------------|
| [AuthService](AuthService/) | Stateless JWT authentication (login, validate, health) |

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

Shared libraries or root-level tooling (e.g. parent POM, docker-compose) can be added at the repository root when needed.
