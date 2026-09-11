# ========================================
# AI 爆款文章创作器 - 后端 Dockerfile
# ========================================

# ============ 构建阶段 ============
# 如果拉取失败，请配置 Docker 镜像加速器，或使用以下国内镜像：
# FROM m.daocloud.io/docker.io/maven:3.9-eclipse-temurin-21-alpine AS build
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# 1. 先复制 pom.xml 并下载依赖（利用 Docker 缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# ============ 运行阶段 ============
# 如果拉取失败，请配置 Docker 镜像加速器，或使用以下国内镜像：
# FROM m.daocloud.io/docker.io/eclipse-temurin:21-jre-alpine
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 安装必要工具（用于健康检查）
RUN apk add --no-cache curl fontconfig ttf-dejavu

# 从构建阶段复制 JAR 包
COPY --from=build /app/target/*.jar app.jar

# 创建非 root 用户运行应用（安全最佳实践）
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser && \
    mkdir -p /data/images /data/model-secrets && chmod 700 /data/model-secrets && \
    chown -R appuser:appuser /app /data/images /data/model-secrets

USER appuser

# 暴露端口
EXPOSE 8123

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8123/api/health/ || exit 1

# JVM 优化参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]
