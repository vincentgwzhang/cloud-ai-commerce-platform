# GatewayService

Spring Cloud Gateway — single HTTP entry (port **8088**) for Product, Inventory, and Order APIs.

Auth/login stays on **AuthService (8080)**; obtain JWT there, then call business APIs through the gateway.

## Routes

| Path prefix | Backend (local) |
|-------------|-----------------|
| `/api/v1/products/**` | `http://localhost:8081` |
| `/api/inventory/**` | `http://localhost:8082` |
| `/api/orders/**` | `http://localhost:8083` |

K8s uses ClusterIP service names (`product-service:80`, etc.).

## Local

```bash
./devops/script/local-dev-setup.sh
# Start backends 8081–8083, then:
cd GatewayService && mvn spring-boot:run
```

## Minikube

```bash
# After Auth + Product + Inventory + Order:
./devops/script/GatewayService/minikube-deploy.sh
kubectl port-forward svc/gateway-service 8088:80
```

Or full stack: `./devops/script/install.sh`
