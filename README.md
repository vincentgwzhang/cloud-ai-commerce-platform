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
├── README.md
├── pom.xml             # Parent POM (dependencyManagement)
├── devops/
│   ├── data/keys/      # RSA keys (generate via devops/script/local-dev-setup.sh)
│   ├── k8s/            # AuthService + ProductService manifests
│   ├── script/         # install.sh, uninstall.sh, local-dev-setup.sh
│   └── db/             # MySQL SQL (manual apply)
├── AuthService/
├── ProductService/
└── postman/
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
chmod +x devops/script/local-dev-setup.sh
./devops/script/local-dev-setup.sh
```

MySQL: `devops/db/init.sql` on database `commerce_platform` (users, products, inventory). Redis on `localhost:6379`.

### Start order in IntelliJ

Use run configurations from `.run/` (imported automatically in IntelliJ):

| Order | Configuration | URL |
|-------|----------------|-----|
| 1 | **AuthService [local]** | http://localhost:8080 |
| 2 | **ProductService [local]** | http://localhost:8081 |

Profile `local`, working directories `AuthService/` and `ProductService/`. Both read **`devops/data/keys/`** (`../devops/data/keys/*.pem`) — no Minikube required.

### Verify

```bash
# Login (AuthService)
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"vincent","password":"123456"}'

# List products (paste accessToken)
curl -s http://localhost:8081/api/v1/products -H "Authorization: Bearer <accessToken>"
```

### Other deploy paths

| Goal | Command |
|------|---------|
| **All services on Minikube** | `./devops/script/install.sh` / `./devops/script/uninstall.sh` |
| Auth in Docker | `./devops/script/AuthService/docker-run.sh` |
| Product in Docker | `./devops/script/ProductService/docker-run.sh` |
| Auth on Minikube only | `./devops/script/AuthService/minikube-deploy.sh` |
| Product on Minikube only | `./devops/script/ProductService/minikube-deploy.sh` |

See [devops/README.md](devops/README.md).

Do not mix tokens: login URL and ProductService must use the **same** Auth deployment and key pair.

## Postman

Import at repository root:

| File | Purpose |
|------|---------|
| [postman/cloud-ai-commerce-platform.postman_collection.json](postman/cloud-ai-commerce-platform.postman_collection.json) | **Auth**, **Product**, **Inventory** folders |
| [postman/cloud-ai-commerce-platform.local.postman_environment.json](postman/cloud-ai-commerce-platform.local.postman_environment.json) | Local URLs (8080/8081/8082), tokens, `inventoryProductCode` |

1. Postman → **Import** → select both files from `postman/` (or collection only).
2. Select environment **Cloud AI Commerce Platform - Local**.
3. Start **AuthService**, run **Auth Service → Login** — saves `accessToken` (Bearer on protected routes) and `refreshToken`.
4. Start **ProductService** (8081), run **Product Service → List Products**.
5. Start **InventoryService** (8082), run **Inventory Service → Get Inventory** (seed SKU `IPHONE17`).
6. When the JWT expires (~1 h), run **Auth Service → Refresh Token** — updates both tokens.

## 如果在 minikube 上面运行
### 1. 部署所有微服务到 Minikube
./devops/script/install.sh

### 2. 创建 metrics 用的 NodePort（让 Prometheus 能抓到指标，无需 port-forward）
./devops/script/observability/minikube-metrics-apply.sh

### 3. 启动基础设施与监控（两个 compose，可各开 terminal）
```bash
# Terminal A — Kafka
docker compose -f devops/script/docker-compose-app.yml up -d

# Terminal B — Prometheus + Grafana（抓 Minikube 指标）
docker compose -f devops/script/docker-compose-observability-minikube.yml up -d
```
Grafana: http://localhost:3000（admin / admin）

## 如果在 Intellij 上运行
```bash
docker compose -f devops/script/docker-compose-app.yml up -d
docker compose -f devops/script/docker-compose-observability-local.yml up -d
```

## 如果要使用 HELM 的方式
```sh
minikube start

# 可选：打 tgz 包
devops/helm/helm-package.sh

# 安装（内部先跑 helm-uninstall，再构建镜像 + helm install）
devops/helm/helm-install.sh

# 卸载（可反复与 install 交替）
devops/helm/helm-uninstall.sh
```