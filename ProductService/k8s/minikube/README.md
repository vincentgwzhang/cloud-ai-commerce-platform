# ProductService on Minikube

**Prerequisite:** [AuthService Minikube deploy](../../AuthService/k8s/minikube/README.md) (JWT + DB secrets).

Host **MySQL** and **Redis** (Ubuntu OS packages, not Docker). Pods use **`host.minikube.internal`** for both.

If Redis works locally (`redis-cli` on `127.0.0.1`) but pods fail with `Connection refused` on `host.minikube.internal:6379`, Redis is almost certainly bound to loopback only — see [minikube-host-services.md](../../../scripts/minikube-host-services.md).

## Deploy (uninstall + rebuild image + apply)

```bash
cd ProductService
chmod +x scripts/minikube-deploy.sh scripts/minikube-uninstall.sh scripts/check-host-redis-minikube.sh
./scripts/minikube-deploy.sh
```

Skip Redis preflight (not recommended): `SKIP_HOST_REDIS_CHECK=1 ./scripts/minikube-deploy.sh`

## Teardown only

```bash
./scripts/minikube-uninstall.sh
```

Does not delete `auth-service-*` secrets or AuthService workloads.

## Test

```bash
AUTH_URL=$(minikube service auth-service --url | head -1)
PRODUCT_URL=$(minikube service product-service --url | head -1)

TOKEN=$(curl -s -X POST "${AUTH_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"vincent","password":"123456"}' | jq -r .accessToken)

curl -s "${PRODUCT_URL}/api/v1/products" -H "Authorization: Bearer ${TOKEN}" | jq
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Connection refused` on `host.minikube.internal:6379` | OS Redis: `bind 0.0.0.0`, `protected-mode no` (dev), `sudo systemctl restart redis-server`; verify `ss -tlnp \| grep 6379` |
| Preflight fails | `./scripts/check-host-redis-minikube.sh` — then [minikube-host-services.md](../../../scripts/minikube-host-services.md) |
| Old ConfigMap still has `REDIS_HOST=redis` | `kubectl delete configmap product-service-config` then redeploy |
| Leftover in-cluster Redis from earlier try | `kubectl delete deployment,service redis --ignore-not-found` |
