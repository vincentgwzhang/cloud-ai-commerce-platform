# DevOps

Central place for **Kubernetes manifests**, **deployment scripts**, and **database SQL** for the commerce platform.

## Layout

```text
devops/
├── README.md
├── data/
│   └── keys/               # RSA private.pem + public.pem (gitignored)
├── k8s/
│   ├── AuthService/          # Deployment, Service, ConfigMap, minikube overrides
│   └── ProductService/
├── script/
│   ├── install.sh            # Uninstall all → deploy Auth → deploy Product
│   ├── uninstall.sh          # Remove all Minikube workloads (idempotent)
│   ├── lib/paths.sh
│   ├── AuthService/          # Per-service scripts (Minikube, Docker, RSA keys)
│   └── ProductService/
├── db/                       # MySQL SQL (manual apply — not run by install.sh)
│   ├── init.sql
│   ├── grant-mysql-docker-access.sql
│   └── update-user-passwords.sql
└── docs/
    └── minikube-host-services.md   # Host MySQL + Redis for Minikube
```

## JWT keys (all environments)

Single location: **`devops/data/keys/`** (IntelliJ, Docker, Minikube).

```bash
./devops/script/local-dev-setup.sh   # generates private.pem + public.pem if missing
```

## Minikube — install / uninstall all services

**Prerequisites:** `minikube start`, JWT keys (`local-dev-setup.sh`), host MySQL (`devops/db/`), host Redis ([docs](docs/minikube-host-services.md)).

```bash
chmod +x devops/script/install.sh devops/script/uninstall.sh
./devops/script/install.sh      # calls uninstall.sh first, then Auth + Product
./devops/script/uninstall.sh    # safe to run again
```

You can alternate `install.sh` and `uninstall.sh` without manual cleanup.

### Port-forward (local testing)

| Service | Command |
|---------|---------|
| Auth | `kubectl port-forward svc/auth-service 8080:80` |
| Product | `kubectl port-forward svc/product-service 8081:80` |

### Per-service only

```bash
./devops/script/AuthService/minikube-deploy.sh
./devops/script/ProductService/minikube-deploy.sh
```

Each deploy script runs its own uninstall first unless `MINIKUBE_SKIP_UNINSTALL=1` (set by `install.sh` after the global uninstall).

## Docker (single service on host)

```bash
./devops/script/AuthService/docker-run.sh
./devops/script/ProductService/docker-run.sh
```

## Database (manual)

Not invoked by `install.sh`. Apply on the Ubuntu host as needed:

```bash
mysql -u vincent -p commerce_platform < devops/db/init.sql
sudo mysql < devops/db/grant-mysql-docker-access.sql
```

See [db/README.md](db/README.md).
