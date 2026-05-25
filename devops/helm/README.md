# Helm — full commerce platform (K8s only)

Helm manages **only** the five microservices on Minikube. **MySQL, Redis, and Kafka are your responsibility** on the host; pods connect via `host.minikube.internal` (see `commerce-platform/values.yaml`).

## What Helm does / does not do

| Helm scripts | Yes | No |
|--------------|-----|-----|
| `helm-package.sh` | Lint + package chart (5 services) | — |
| `helm-install.sh` | JWT secrets, build 5 images, `helm upgrade --install` | Start/stop MySQL, Redis, Kafka |
| `helm-uninstall.sh` | Remove release + K8s workloads/secrets | Touch host MySQL/Redis/Kafka |

## Host dependencies (you start these)

| Service | Typical on host | From inside Minikube pods |
|---------|-----------------|---------------------------|
| MySQL | `127.0.0.1:3306`, user `vincent` | `host.minikube.internal:3306` |
| Redis | `127.0.0.1:6379`, no password | `host.minikube.internal:6379` |
| Kafka | `127.0.0.1:9092` (your setup) | `host.minikube.internal:9092` |

Override hosts in `values.yaml` or `helm install --set dependencies.mysql.host=...`.

## Quick start

```bash
minikube start

# 1) You: start MySQL, Redis, Kafka on the host

# 2) Package chart
devops/helm/helm-package.sh

# 3) Deploy microservices (builds images unless SKIP_BUILD=1)
devops/helm/helm-install.sh
```

## Microservices in chart

Auth, Product, Inventory, Order, Gateway — see `commerce-platform/templates/`.

## Idempotent cycle

```bash
cd devops/helm
./helm-install.sh && ./helm-uninstall.sh && ./helm-install.sh
```

Uninstall leaves host MySQL/Redis/Kafka running.
