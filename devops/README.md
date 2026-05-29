# Run the platform — 3 ways

Six services: **auth (8080) · product (8081) · inventory (8082) · order (8083) · ai (8084) · gateway (8088)**.

Pick **one** of the scenarios below. Every scenario is **idempotent**: it assumes leftovers from a
previous run (docker compose containers and/or Minikube workloads) and cleans up first, so you can
re-run it any number of times.

---

## Host services you manage (needed by all scenarios)

These are **not** started by the scripts — run them yourself once:

- **MySQL** `:3306` — user `vincent` / `1q2w3e4R`, database `commerce_platform`
- **Redis** `:6379` — no auth

Apply the schema (idempotent — `CREATE TABLE IF NOT EXISTS`, includes the `ai_*` tables):

```bash
mysql -h 127.0.0.1 -u vincent -p commerce_platform < devops/db/init.sql
```

For Minikube (scenarios 2 & 3) MySQL must also accept connections from the cluster:

```bash
sudo mysql < devops/db/grant-mysql-docker-access.sql
```

`OPENAI_API_KEY` is optional — services boot without it, but ai-service chat/RAG calls to OpenAI fail until provided.

---

## Scenario 1 — Local (run on host, just booted)

Run the Spring Boot apps on your machine (IntelliJ or `mvn`), with Kafka + Chroma + Grafana in Docker.

### 1. Reset infra (idempotent)

```bash
# JWT keys (created only if missing)
./devops/script/local-dev-setup.sh --keys-only

# Tear down any previous compose stacks, then bring them up fresh
docker compose -f devops/script/docker-compose-app.yml down
docker compose -f devops/script/docker-compose-observability-local.yml down
docker compose -f devops/script/docker-compose-app.yml up -d                  # Kafka + Kafka UI
docker compose -f devops/script/docker-compose-observability-local.yml up -d  # Prometheus + Grafana + Chroma
```

> Add `-v` to a `down` to also wipe its volumes (Kafka data / Chroma vectors / Grafana state).

### 2. Run the services (profile `local`, connects to localhost)

IntelliJ: run each `*Application` with the default `local` profile. Or from the command line, one terminal each:

```bash
mvn -pl AuthService      spring-boot:run
mvn -pl ProductService   spring-boot:run
mvn -pl InventoryService spring-boot:run
mvn -pl OrderService     spring-boot:run
OPENAI_API_KEY=sk-... mvn -pl AiService spring-boot:run
mvn -pl GatewayService   spring-boot:run
```

### 3. Access

| What | URL |
|------|-----|
| Services | http://localhost:8080 / 8081 / 8082 / 8083 / 8084 / 8088 |
| Kafka UI | http://localhost:18080 |
| Grafana | http://localhost:3000 (admin / admin) |
| Chroma | http://localhost:8000 |

---

## Scenario 2 — Minikube + kubectl (full teardown, then redeploy)

Builds images into Minikube and deploys raw manifests with `kubectl`.

### 1. Clean slate (idempotent — clears Helm, ArgoCD, or kubectl leftovers)

```bash
# If you ever set up GitOps, remove the ArgoCD app first or it will re-create everything
kubectl delete -n argocd application commerce-platform --ignore-not-found

# Remove any previous Helm release (no-op if absent)
helm uninstall commerce-platform 2>/dev/null || true

# Remove kubectl workloads (safe to run repeatedly)
./devops/script/uninstall.sh
```

### 2. Infra + deploy

```bash
minikube start                                                                # if not running
docker compose -f devops/script/docker-compose-app.yml up -d                  # Kafka
docker compose -f devops/script/docker-compose-observability-minikube.yml up -d   # Chroma + Prometheus + Grafana

# Build all 6 images into Minikube and deploy (this also runs uninstall.sh internally)
OPENAI_API_KEY=sk-... ./devops/script/install.sh

# Optional: expose metrics so Grafana works (re-run after every cluster rebuild)
./devops/script/observability/minikube-metrics-apply.sh
```

### 3. Access

```bash
kubectl port-forward svc/gateway-service 8088:80
kubectl port-forward svc/ai-service      8084:80
# auth 8080 / product 8081 / inventory 8082 / order 8083 similarly
```

Teardown anytime: `./devops/script/uninstall.sh`

---

## Scenario 3 — Minikube + Helm (full teardown, then redeploy)

Packages all 6 services into one Helm chart and deploys the release.

### 1. Clean slate (idempotent)

```bash
# Remove the ArgoCD app first or it will re-create everything
kubectl delete -n argocd application commerce-platform --ignore-not-found

# Removes the Helm release AND any legacy kubectl workloads (safe to run repeatedly)
./devops/helm/helm-uninstall.sh
```

### 2. Infra + deploy

```bash
minikube start                                                                # if not running
docker compose -f devops/script/docker-compose-app.yml up -d                  # Kafka
docker compose -f devops/script/docker-compose-observability-minikube.yml up -d   # Chroma + Prometheus + Grafana

# Build all 6 images + helm upgrade --install (runs helm-uninstall.sh internally first)
OPENAI_API_KEY=sk-... ./devops/helm/helm-install.sh

# Optional: expose metrics so Grafana works (re-run after every cluster rebuild)
./devops/script/observability/minikube-metrics-apply.sh
```

### 3. Access

```bash
kubectl port-forward svc/gateway-service 8088:80
kubectl port-forward svc/ai-service      8084:80
# auth 8080 / product 8081 / inventory 8082 / order 8083 similarly
```

Teardown anytime: `./devops/helm/helm-uninstall.sh`

---

### Notes

- **Do not mix scenarios 2 and 3 at the same time** — they deploy the same resource names to the
  same namespace. Always run the clean-slate step of the scenario you are switching to.
- **ArgoCD vs manual:** scenarios 2 & 3 are manual. If you want GitOps back, re-apply
  `devops/argocd/applications/commerce-platform.application.yaml`. While that Application exists
  with auto-sync, it will fight manual `kubectl`/`helm` changes — hence deleting it first above.
- `OPENAI_API_KEY` passed to `install.sh` / `helm-install.sh` is stored as the `ai-service-secret`
  Kubernetes Secret. Omit it to deploy without OpenAI access.
