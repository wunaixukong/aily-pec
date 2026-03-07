# 使用 Java 17 基础镜像
FROM eclipse-temurin:17-jdk-alpine

# 设置工作目录
WORKDIR /app

# 复制 jar 包
COPY target/aily-pec-1.0.0.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
