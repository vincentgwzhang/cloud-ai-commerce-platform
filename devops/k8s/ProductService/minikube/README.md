# ProductService on Minikube

Manifests: `devops/k8s/ProductService/`. Requires AuthService secrets on the cluster.

```bash
../../script/install.sh
# or Product only (after Auth):
../../script/ProductService/minikube-deploy.sh
```

Host **MySQL** and **Redis** (OS install) via `host.minikube.internal` — see [minikube-host-services.md](../../../docs/minikube-host-services.md).

## Test

```bash
AUTH_URL=$(minikube service auth-service --url | head -1)
PRODUCT_URL=$(minikube service product-service --url | head -1)

TOKEN=$(curl -s -X POST "${AUTH_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"vincent","password":"123456"}' | jq -r .accessToken)

curl -s "${PRODUCT_URL}/api/v1/products" -H "Authorization: Bearer ${TOKEN}" | jq
```

## Port-forward

```bash
kubectl port-forward svc/product-service 8081:80
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Redis `Connection refused` on `host.minikube.internal` | `bind 0.0.0.0` in `/etc/redis/redis.conf` — see host services doc |
| Preflight fails | `devops/script/ProductService/check-host-redis-minikube.sh` |
| Missing JWT secret | Deploy Auth first (`install.sh` or Auth `minikube-deploy.sh`) |
