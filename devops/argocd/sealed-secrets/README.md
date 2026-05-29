# Sealed Secrets (Bitnami) — secrets in Git, safely

Lets us keep `ai-service`'s `OPENAI_API_KEY` under GitOps **without** committing a plaintext key.

## How it works

```
plaintext key  --kubeseal-->  ciphertext (values-sealed.yaml)  --git push-->  Argo CD
                                                                                  |
                                                                            sync SealedSecret
                                                                                  |
                                                       sealed-secrets controller (holds private key)
                                                                                  |
                                                                     decrypts -> Secret ai-service-secret
                                                                                  |
                                                                     ai-service envFrom (optional)
```

- The ciphertext is encrypted against the controller's public cert. Only the controller's
  in-cluster private key can decrypt it, so the file is safe to commit (GitHub push protection
  will not flag it — it is not a recognizable key).
- Scope is `strict`: the sealed value only decrypts into a Secret named `ai-service-secret`
  in namespace `default`. Renaming either breaks decryption (by design).

## One-time setup

```bash
# 1) Cluster must be up
minikube start

# 2) Install controller (in-cluster) + kubeseal (CLI)
devops/argocd/sealed-secrets/install-sealed-secrets.sh
```

## Seal / rotate the key

```bash
OPENAI_API_KEY=sk-... devops/argocd/sealed-secrets/seal-ai-openai-key.sh
git add devops/helm/commerce-platform/values-sealed.yaml
git commit -m "chore: seal ai-service OpenAI key"
git push
```

Argo CD syncs the chart, applies the `SealedSecret`, and the controller materializes the
`ai-service-secret` Secret. Re-run the seal script anytime to rotate.

## Caveats

- The controller's private key lives in the cluster. If you delete/recreate Minikube, the old
  ciphertext can no longer be decrypted — just re-run the seal script against the new cluster.
- This is the **GitOps path** only. The imperative `devops/helm/helm-install.sh` and
  `devops/script/AiService/minikube-deploy.sh` still create the Secret directly from the
  `OPENAI_API_KEY` env var (no sealing needed for local runs).
