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

---

## Scenario 4 — GitOps with Argo CD (+ Sealed Secrets)

Instead of running Helm by hand (scenario 3), let **Argo CD** sync the Helm chart from Git, and ship
the OpenAI key as encrypted **Sealed Secrets** ciphertext that is safe to commit.

### One-time install (on a fresh cluster)

```bash
minikube start

# 1. Argo CD
./devops/argocd/install-argocd.sh

# 2. Sealed Secrets controller + kubeseal CLI
./devops/argocd/sealed-secrets/install-sealed-secrets.sh

# 3. Non-Git secrets (JWT keys + DB creds) — created out-of-band, not stored in Git
./devops/argocd/bootstrap-platform-secrets.sh
```

### Seal the OpenAI key and push (re-run anytime to rotate)

```bash
OPENAI_API_KEY=sk-... ./devops/argocd/sealed-secrets/seal-ai-openai-key.sh
git add devops/helm/commerce-platform/values-sealed.yaml
git commit -m "chore: seal ai-service OpenAI key" && git push
```

### Point Argo CD at the repo

Edit `repoURL` in `devops/argocd/applications/commerce-platform.application.yaml`, then:

```bash
kubectl apply -f devops/argocd/applications/commerce-platform.application.yaml
```

Argo CD now renders the chart and reconciles the cluster to Git. Push a change → it syncs.
(Images must exist in Minikube — build them once via `helm-install.sh` or `install.sh`.)

---

## Reset / teardown — pick the level

From the lightest reset to a full wipe. Each command is idempotent.

### Level 1 — platform app only (most common)

Remove the 6 services + their config/secrets/metrics. **If Argo CD is auto-syncing, delete the
Application first or it will recreate everything.**

```bash
kubectl delete -n argocd application commerce-platform --ignore-not-found
./devops/helm/helm-uninstall.sh          # Helm release + legacy kubectl workloads
# add REMOVE_MINIKUBE_IMAGES=1 to also drop the built images
```

### Level 2 — remove GitOps tooling

```bash
./devops/argocd/uninstall-argocd.sh                          # Argo CD (keeps platform workloads)
./devops/argocd/sealed-secrets/uninstall-sealed-secrets.sh   # Sealed Secrets controller + sealing key
```

> Removing the Sealed Secrets controller deletes the in-cluster private key, so the committed
> `values-sealed.yaml` ciphertext becomes undecryptable. After reinstalling you must **re-seal** the key.

### Level 3 — stop host infrastructure

```bash
docker compose -f devops/script/docker-compose-app.yml down
docker compose -f devops/script/docker-compose-observability-minikube.yml down   # or -local
# MySQL / Redis are yours to stop
```

### Level 4 — nuke everything (true clean slate)

```bash
minikube delete            # removes the cluster: apps, Argo CD, Sealed Secrets, images, all
```

After `minikube delete` you start from zero: `minikube start` → re-run the install steps for your
chosen scenario (and, for GitOps, reinstall Argo CD + Sealed Secrets and **re-seal** the key).

---

## From zero on a new machine

1. Install prerequisites (top of this doc): JDK 25, Maven, Docker, Minikube, kubectl, Helm, MySQL, Redis.
2. Apply DB schema: `mysql -h 127.0.0.1 -u vincent -p commerce_platform < devops/db/init.sql`
3. Generate JWT keys: `./devops/script/local-dev-setup.sh`
4. Pick a path:
   - Local dev → **Scenario 1**
   - Minikube manual → **Scenario 2 (kubectl)** or **Scenario 3 (Helm)**
   - GitOps → **Scenario 4 (Argo CD + Sealed Secrets)**
