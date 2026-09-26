# ---- 构建阶段：Maven + JDK21 ----
# 国内拉不动 docker.io 时：先用 compose 同目录的命令打 tag（见 deploy/README.md）
FROM maven:3.9-eclipse-temurin-21 AS build
# 国内构建传 --build-arg MAVEN_MIRROR=... 覆盖；海外可不传
ARG MAVEN_MIRROR=https://maven.aliyun.com/repository/public
WORKDIR /src
COPY pom.xml .
# 先拉依赖（利用 Docker 层缓存，源码变更不重下依赖）；Maven 走指定镜像加速
RUN mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR" > /root/.m2/settings.xml \
    && mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---- 运行阶段：JRE21 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# 非 root 运行
RUN useradd -r -u 10001 app && mkdir -p /app/data /app/uploads && chown -R app:app /app
USER app
COPY --from=build /src/target/server-0.1.0.jar app.jar
# 数据目录（SQLite / 上传文件 / APK）挂卷持久化；默认 SQLite 档，生产建议 DB_URL 指 Postgres
ENV SPRING_PROFILES_ACTIVE=dev \
    JWT_SECRET="" \
    GLM_API_KEY=""
EXPOSE 8080
VOLUME ["/app/data"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD curl -sf http://127.0.0.1:8080/api/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
