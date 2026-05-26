# Argo CD + Helm — 原理与使用计划

本文说明 **Argo CD 如何与 Helm 一起做「版本一致性」**，以及在本仓库（Minikube + 自建 MySQL/Redis/Kafka）下的推荐步骤。

---

## 一、原理：三层「期望状态」

可以把它想成三条链，**一致** = 三条链能对上号：

```text
Git（事实来源）          Helm Chart + values（期望清单）       Kubernetes（实际状态）
─────────────────────────────────────────────────────────────────────────────────────
commit abc123      →     commerce-platform/values.yaml      →     Deployment 镜像 tag
                         Chart.yaml version 0.1.0                 replicaCount=2
                         templates/*.yaml                         ...
```

| 角色 | 做什么 | 是否负责打镜像 |
|------|--------|----------------|
| **Git** | 存 Chart、values、（可选）环境分支 `values-minikube.yaml` | 否 |
| **Helm** | 把 Chart + values **渲染**成 YAML | 否 |
| **Argo CD** | 对比 Git 渲染结果 vs 集群，执行 **Sync** | 否 |
| **你 / CI** | `mvn package` + `docker build/push`，改 values 里的 `image.tag` | 是 |

Argo CD **不会**替你跑 `helm-install.sh` 里的 Maven/Docker；它只保证：

> **集群里跑的资源 = Git 里声明的那份 Helm 配置（在某个 commit / tag 上）。**

这就是 ChatGPT 说的「Git 和 K8s 版本一致」在工程上的含义——不是魔法，是 **单一事实来源（Git）+ 自动同步 + 可审计的 Revision**。

---

## 二、Argo CD 核心概念（和 Helm 的对应关系）

| Argo CD 概念 | 含义 | 对应你现在有什么 |
|--------------|------|------------------|
| **Application** | 一个「要同步的应用」，指向 Git 路径 + Helm | `devops/helm/commerce-platform` |
| **Source** | `repoURL` + `targetRevision`（分支/tag/commit）+ `path` | 你的 Git 远程仓库 |
| **Destination** | 同步到哪个集群、哪个 namespace | Minikube `default` |
| **Sync** | 执行 `helm template` 并 apply 到集群 | 替代手跑 `helm upgrade` |
| **Revision** | 每次同步对应的 **Git commit SHA** | `helm history` 记的是 Helm revision；Argo 界面显示 **Git SHA** |
| **Drift（漂移）** | 有人 `kubectl edit` 改了集群，和 Git 不一致 | Argo 标 **OutOfSync**，可 Self-Heal 改回 |
| **Rollback** | 回到 **上一个 Git 同步记录** 或指定 commit | UI / `argocd app rollback` |

和「只跑 helm-install」的差别：

| | `helm-install.sh` | Argo CD |
|--|-------------------|---------|
| 触发 | 你本机执行脚本 | Git 变更或手动 Sync |
| 版本记录 | `helm history`（Revision 1,2,3…） | Argo UI 里的 **Git commit** + Helm revision |
| 别人复现 | 要有你的本机镜像构建 | **同一 Git commit + 同一镜像 tag**（镜像需在 registry 或 Minikube 里已有） |
| 手改集群 | 下次 install 可能覆盖 | Self-Heal 可自动改回 Git |

---

## 三、本项目的边界（避免误解）

Argo CD **仍然不管**：

- 宿主机 MySQL / Redis / Kafka（你自行启动）
- JWT 私钥进 Git（**不能**）；Secret 用 `bootstrap-platform-secrets.sh` 预先创建
- Docker 镜像构建（用 `helm-install.sh` 或 CI 构建后，把 **tag 写进 Git 的 values**）

Argo CD **会管**：

- 5 个微服务的 Deployment / Service / ConfigMap（来自 Helm Chart）

---

## 四、推荐实施计划（分阶段，不要跳步）

### 阶段 0 — 你现在（已完成）

- [x] Helm Chart：`devops/helm/commerce-platform`
- [x] 手动：`helm-package.sh` / `helm-install.sh`

### 阶段 1 — 安装 Argo CD（只加工具，不改业务）

- [ ] `devops/argocd/install-argocd.sh` 在 Minikube 安装 Argo CD
- [ ] 能打开 UI，看到 `argocd` namespace

### 阶段 2 — Git 成为「期望状态」来源

- [ ] 代码 **push 到远程 Git**（Argo CD 必须从远程拉仓库，不能只靠本机未 push 的改动）
- [ ] 在 Argo CD 注册仓库（HTTPS + PAT 或 SSH deploy key）
- [ ] 修改 `applications/commerce-platform.application.yaml` 里的 `repoURL` / `targetRevision`

### 阶段 3 — 第一次 GitOps 同步

- [ ] 宿主机：MySQL / Redis / Kafka 已启动
- [ ] `bootstrap-platform-secrets.sh` 创建 JWT/DB Secret
- [ ] 本机构建镜像进 Minikube（**一次性**）：`SKIP_BUILD=0 devops/helm/helm-install.sh` 仅构建，或自己对 5 个服务 `docker build`
- [ ] 确认 `values.yaml` 里 `image.tag` 与 Minikube 里镜像 tag 一致
- [ ] Apply Application → Argo **Sync** → 5 个 Deployment 由 Argo 管理

### 阶段 4 — 体验「版本一致」

1. 在 Git 改 `values.yaml`（例如 `services.product.replicaCount: 2`）→ commit → push  
2. Argo 显示 **OutOfSync** → Sync  
3. `kubectl get deploy product-service` 副本变为 2  
4. Argo UI 里看到本次同步的 **Git commit SHA** = 集群状态来源  

再试 **Rollback**：在 Argo 回滚到上一同步版本，副本应恢复。

### 阶段 5 — 与 CI 对齐（以后）

```text
git tag v1.0.1 → CI build/push image:1.0.1 → 改 values image.tag → push → Argo auto-sync
```

可选：Argo CD Image Updater（自动改 tag，学习阶段可不上）。

---

## 五、日常操作速查

```bash
# 1) 安装 Argo CD
devops/argocd/install-argocd.sh

# 2) 取初始 admin 密码并端口转发 UI
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo
kubectl port-forward svc/argocd-server -n argocd 8080:443

# 3) 部署前：Secret + 镜像（Argo 不做这两步）
devops/argocd/bootstrap-platform-secrets.sh
# 构建镜像：devops/helm/helm-install.sh 或自有 CI

# 4) 编辑 Application 填 repoURL 后
kubectl apply -f devops/argocd/applications/commerce-platform.application.yaml

# 5) CLI 同步（可选）
argocd app sync commerce-platform
argocd app history commerce-platform
```

---

## 六、和 `helm-install.sh` 怎么共存？

| 场景 | 用谁 |
|------|------|
| 本地快速试、要自动 mvn/docker build | `helm-install.sh` |
| 练 GitOps、版本与 commit 对齐 | Argo CD Application |
| 两者不要同时管同一 Release | 二选一，或先 uninstall Helm release 再交给 Argo |

从 Helm 切到 Argo 时建议：

```bash
helm uninstall commerce-platform -n default   # 若曾用 helm install
# 然后只让 Argo CD Sync
```

---

## 七、安装故障：`metadata.annotations: Too long`

若出现：

```text
CustomResourceDefinition "applicationsets.argoproj.io" is invalid:
metadata.annotations: Too long: may not be more than 262144 bytes
```

原因：`kubectl apply` 默认把整份 manifest 写进 `last-applied-configuration` 注解，Argo CD 的 CRD 太大（常见于 Kubernetes 1.29+）。

**处理：** 使用已更新的 `install-argocd.sh`（内部为 `--server-side --force-conflicts`）。若第一次安装失败留下半成品，直接**再跑一遍**同一脚本即可。

```bash
devops/argocd/install-argocd.sh
```

仍失败时，可先清理再装：

```bash
kubectl delete namespace argocd --wait=true
devops/argocd/install-argocd.sh
```

---

## 八、文件说明

| 文件 | 作用 |
|------|------|
| `install-argocd.sh` | 安装 Argo CD 到集群 |
| `bootstrap-platform-secrets.sh` | 创建 JWT/DB Secret（不进 Git） |
| `applications/commerce-platform.application.yaml` | Application 定义（需改 `repoURL`） |

更细的 Helm 打包说明见 [../helm/README.md](../helm/README.md).
