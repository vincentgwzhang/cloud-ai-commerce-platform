# Deploy AuthService on Minikube

MySQL runs on your **host** (Ubuntu). Pods inside Minikube must use **`host.minikube.internal`** as `DB_HOST` (not `mysql`, not `localhost`).

## Prerequisites

- `minikube start` completed
- MySQL on host: `bind-address` allows remote (e.g. `0.0.0.0`), user `vincent@%` (see `scripts/grant-mysql-docker-access.sql`)
- RSA keys under `AuthService/data/keys/` (`./scripts/generate-rsa-keys.sh`)

## 1. Point Docker at Minikube’s daemon (build image Minikube can use)

```bash
eval $(minikube docker-env)
cd /path/to/cloud-ai-commerce-platform/AuthService
mvn -DskipTests package
./scripts/generate-rsa-keys.sh
docker build -t auth-service:1.0.0 .
```

Without this step, Kubernetes pulls from Docker Hub and won’t find your local image.

**Already built on the host?** Load it into Minikube without rebuilding:

```bash
minikube image load auth-service:1.0.0
kubectl rollout restart deployment/auth-service
```

(`deployment.yaml` uses tag **`1.0.0`**, not `1.0` — retag if needed: `docker tag auth-service:1.0 auth-service:1.0.0`)

## 2. Leave Minikube Docker context (optional, for normal `docker` on host)

```bash
eval $(minikube docker-env -u)
```

## 3. Create secrets (JWT keys + DB password)

From `AuthService/`:

```bash
kubectl create secret generic auth-service-jwt-keys \
  --from-file=private.pem=./data/keys/private.pem \
  --from-file=public.pem=./data/keys/public.pem \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic auth-service-secret \
  --from-literal=DB_USERNAME=vincent \
  --from-literal=DB_PASSWORD='YOUR_REAL_PASSWORD' \
  --dry-run=client -o yaml | kubectl apply -f -
```

## 4. Apply manifests

```bash
kubectl apply -f k8s/minikube/configmap-host-mysql.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

## 5. Open the app from your laptop

```bash
minikube service auth-service --url
```

Or port-forward:

```bash
kubectl port-forward svc/auth-service 8080:80
# then http://localhost:8080/api/v1/auth/health
```

## Troubleshooting

| Symptom | Check |
|---------|--------|
| `ImagePullBackOff` / `ErrImagePull` | Image built on **host** Docker — run `minikube image load auth-service:1.0.0`, or rebuild after `eval $(minikube docker-env)` |
| `CrashLoopBackOff` + DB errors | `DB_HOST` must be `host.minikube.internal`; MySQL `bind-address` and `vincent@%` |
| Old ConfigMap | `kubectl delete configmap auth-service-config` then re-apply minikube ConfigMap |

## One-shot scripts

Shared logic: `scripts/minikube-lib.sh` (RSA keys + Minikube image cleanup).

| Script | Action |
|--------|--------|
| `scripts/minikube-deploy.sh` | **Runs uninstall first**, then keys → rebuild image → secrets + manifests |
| `scripts/minikube-uninstall.sh` | Teardown only (also invoked automatically at start of deploy) |

Typical workflow — **only deploy**:

```bash
./scripts/minikube-deploy.sh
```

Teardown without redeploy:

```bash
./scripts/minikube-uninstall.sh
```

Options:

```bash
FORCE_RSA_REGENERATE=1 ./scripts/minikube-deploy.sh   # new RSA key pair before deploy
REMOVE_LOCAL_RSA_KEYS=1 ./scripts/minikube-uninstall.sh   # also delete data/keys/*.pem
REMOVE_MINIKUBE_IMAGE=0 ./scripts/minikube-uninstall.sh   # keep image in Minikube
```
