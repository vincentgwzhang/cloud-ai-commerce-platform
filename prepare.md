# Clean Environment

```bash
minikube stop
minikube delete --all --purge

sudo rm -f /usr/local/bin/minikube
sudo rm -f /usr/local/bin/kubectl

rm -rf ~/.kube
rm -rf ~/.minikube

docker system prune -a --volumes
```

# Install kubectl

```bash
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"

sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

kubectl version --client
```

# Install Minikube

```bash
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64

sudo install minikube-linux-amd64 /usr/local/bin/minikube

minikube version
```

# Install Helm

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

helm version
```

# Install K9s

```bash
curl -sS https://webinstall.dev/k9s | bash

k9s version
```

# Start Minikube

```bash
minikube start --driver=docker --cpus=6 --memory=8192
```

# Verify Kubernetes

```bash
kubectl get nodes

kubectl get pods -A

minikube status

helm ls -A
```

# Test Deployment

```bash
kubectl create deployment test-nginx --image=nginx

kubectl get pods

kubectl delete deployment test-nginx
```

# Enable Metrics Server

```bash
minikube addons enable metrics-server

kubectl top nodes
```

# Enable Dashboard

```bash
minikube addons enable dashboard

minikube dashboard
```