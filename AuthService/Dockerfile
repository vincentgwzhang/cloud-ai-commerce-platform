# 使用 Eclipse Temurin 的 Java 25 JRE Alpine 镜像作为最终运行环境。
# 这里选择 JRE 而不是 JDK，是因为容器里只需要运行已经打包好的 Spring Boot jar，
# 不需要 javac、Maven 等编译工具；这样镜像更小，暴露的运行面也更少。
# alpine 版本基于 Alpine Linux，体积通常更小，但也要权衡 musl libc 兼容性：
# 如果应用依赖某些 native library，Debian/Ubuntu 系列镜像有时会更稳。
# AS runtime 给这个构建阶段起名叫 runtime；虽然当前 Dockerfile 只有一个阶段，
# 但命名后更清楚表达“这是运行阶段”，以后扩展成多阶段构建时也可以被 COPY --from=runtime 引用。
FROM eclipse-temurin:25-jre-alpine AS runtime

# 设置容器内的工作目录。
# 后续 COPY、RUN、ENTRYPOINT 等相对路径都会以 /app 为基准。
WORKDIR /app

# 创建一个名为 spring 的系统用户组和系统用户。
# -S 表示创建 system group/user，不创建普通登录用户需要的额外内容。
# 这样应用运行时不会使用 root 身份，降低容器被攻破后的权限风险。
RUN addgroup -S spring && adduser -S spring -G spring

# 切换到 spring 用户运行后续命令和最终应用进程。
# 这意味着 java 进程默认没有 root 权限。
USER spring:spring

# 这个 Dockerfile 需要从仓库根目录作为 build context 来构建：
# docker build -f AuthService/Dockerfile .
# 因为下面 COPY 的路径同时引用了 AuthService/target 和 devops/data/keys。

# 把 Maven 打包出来的 AuthService jar 复制进镜像，并重命名为 app.jar。
# 这里假设宿主机上已经先执行过 Maven package，例如 mvn -pl AuthService package。
COPY AuthService/target/auth-service-*.jar app.jar

# 把本地开发/部署用的 RSA key 复制到容器内。
# private.pem 用来签发 JWT，public.pem 用来验证 JWT。
# 生产环境里通常更推荐通过 secret、volume 或平台密钥管理系统注入，而不是 bake 进镜像。
COPY devops/data/keys/ /app/keys/

# 设置应用运行时需要的环境变量。
# SERVER_PORT 指定 Spring Boot 监听端口。
# JWT_PRIVATE_KEY_PATH 和 JWT_PUBLIC_KEY_PATH 告诉 RsaKeyLoader 从容器内哪里读取 RSA 密钥。
ENV SERVER_PORT=8080 \
    JWT_PRIVATE_KEY_PATH=file:/app/keys/private.pem \
    JWT_PUBLIC_KEY_PATH=file:/app/keys/public.pem

# 声明容器内应用会监听 8080 端口。
# EXPOSE 本身不会真正发布端口；运行容器时仍需要 docker run -p 或 docker compose ports 映射。
EXPOSE 8080

# 容器启动时执行 Spring Boot jar。
# JSON 数组写法不会经过 shell，信号转发更直接，容器停止时 Java 进程能更正常地收到 SIGTERM。
ENTRYPOINT ["java", "-jar", "app.jar"]
