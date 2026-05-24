# Shared JWT keys

| File | Use |
|------|-----|
| `keys/private.pem` | AuthService signs JWTs (local, Docker, Minikube) |
| `keys/public.pem` | AuthService + ProductService verify JWTs |

Generate (idempotent):

```bash
../script/local-dev-setup.sh
# keys only:
../script/local-dev-setup.sh --keys-only
```

`*.pem` files are gitignored.
