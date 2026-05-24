# Host MySQL & Redis for Minikube

Minikube pods reach services on your **Ubuntu host** via **`host.minikube.internal`** (not `localhost` inside the pod).

| Service | ConfigMap keys | Default OS install pitfall |
|---------|----------------|----------------------------|
| MySQL | `DB_HOST`, `DB_PORT` | Listens only on `127.0.0.1`; user `vincent@localhost` only |
| Redis | `REDIS_HOST`, `REDIS_PORT` | `bind 127.0.0.1`; `protected-mode yes` blocks non-local clients |

`redis-cli` / `mysql` on the host using `127.0.0.1` can work while **pods still get Connection refused** — the process is not accepting TCP on the Minikube gateway IP (often `192.168.49.1`).

## MySQL (AuthService + ProductService)

1. Allow remote bind (e.g. in `/etc/mysql/mysql.conf.d/mysqld.cnf`):

   ```ini
   bind-address = 0.0.0.0
   ```

2. Grant user from any host (from `AuthService/`):

   ```bash
   sudo mysql < scripts/grant-mysql-docker-access.sql
   sudo systemctl restart mysql
   ```

3. From the host, test via the gateway (not only `localhost`):

   ```bash
   mysql -h 127.0.0.1 -u vincent -p commerce_platform -e 'SELECT 1'
   # If bind is 0.0.0.0, also try the address shown in: minikube ssh -- getent hosts host.minikube.internal
   ```

## Redis (ProductService)

OS package (`redis-server`), not Docker.

1. Edit Redis config (Ubuntu package: `/etc/redis/redis.conf`):

   ```conf
   # Accept connections from Minikube (host gateway), not only loopback:
   bind 0.0.0.0 -::1

   # Dev Minikube only — or keep protected-mode and set REDIS_PASSWORD in ConfigMap
   protected-mode no
   ```

2. Restart:

   ```bash
   sudo systemctl restart redis-server
   ```

3. Confirm listening address:

   ```bash
   ss -tlnp | grep 6379
   # Expect *:6379 or 0.0.0.0:6379 — not only 127.0.0.1:6379
   ```

4. Test from the host using the Minikube host IP (gateway):

   ```bash
   HOST_IP=$(ip -4 route show default dev $(minikube profile list -o json 2>/dev/null | jq -r '.[0].Name' 2>/dev/null || echo "") 2>/dev/null | awk '{print $3}')
   # Simpler: use the IP from pod errors, e.g. 192.168.49.1
   redis-cli -h 192.168.49.1 ping
   ```

   Should reply `PONG`. If `Could not connect`, Redis is still loopback-only or firewalled.

5. Optional: from a one-off Minikube pod:

   ```bash
   kubectl run redis-host-check --rm -i --restart=Never --image=busybox:1.36 -- \
     sh -c 'nc -zvw3 host.minikube.internal 6379 && echo OK'
   ```

## ProductService ConfigMap

`ProductService/k8s/minikube/configmap-host-mysql.yaml` sets:

- `DB_HOST=host.minikube.internal`
- `REDIS_HOST=host.minikube.internal`

No in-cluster Redis Deployment is used when following this layout.
