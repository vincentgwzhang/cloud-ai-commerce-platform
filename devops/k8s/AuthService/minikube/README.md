# AuthService on Minikube

Manifests live under `devops/k8s/AuthService/`. Deploy with:

```bash
# All services
../../script/install.sh

# Auth only
../../script/AuthService/minikube-deploy.sh
```

MySQL on the **host** — `DB_HOST=host.minikube.internal` in `minikube/configmap-host-mysql.yaml`.

## Prerequisites

- `minikube start`
- Host MySQL: `devops/db/grant-mysql-docker-access.sql`, `bind-address` allows remote
- RSA keys: `devops/script/local-dev-setup.sh` (or `--keys-only`) → `devops/data/keys/`
- ProductService needs host Redis: [minikube-host-services.md](../../../docs/minikube-host-services.md)

## Manual apply

```bash
kubectl apply -f devops/k8s/AuthService/minikube/configmap-host-mysql.yaml
kubectl apply -f devops/k8s/AuthService/deployment.yaml
kubectl apply -f devops/k8s/AuthService/service.yaml
```

## Access

```bash
minikube service auth-service --url
kubectl port-forward svc/auth-service 8080:80
```

## Troubleshooting

| Symptom | Check |
|---------|--------|
| `ImagePullBackOff` | Image built inside Minikube Docker (`minikube-deploy.sh`) |
| DB connection errors | `host.minikube.internal`, MySQL grants, `bind-address` |
| Stale ConfigMap | `kubectl delete configmap auth-service-config` and redeploy |
