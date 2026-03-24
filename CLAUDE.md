# CLAUDE.md - 项目指南

## 项目概况
- **名称**: aily-pec (胸大鸡)
- **技术栈**: Java 17, Spring Boot, MySQL, Redis, Docker
- **部署**: 使用 `./deploy.sh` 进行本地编译、上传和远程部署

## 运行规则 (Critical)
- **Git 流程**:
  - 每次创建新文件后，必须立即执行 `git add <file>`。
  - **禁止自动 commit**: 除非用户明确发出指令（如“提交代码”或使用 `/commit`），否则不要执行 commit 操作。
- **代码规范**:
  - **方法**: 每个方法（特别是抽取出的方法）必须有功能注释。
  - **类与实体**: 新增类必须有类说明；实体类（Entity/DT每个字段**都必须有注释说明含义。
- **语言**: 始终使用 **中文** 与用户交流和书写文档/注释。
- **环境**: Windows 开发环境，Bash 终端。

## 开发规范
- **DTO**: 统一放在 `com.ailypec.dto` 及其子包下，使用 Lombok `@Data`。
- **Service**: 业务逻辑核心，需处理事务 (`@Transactional`)。
- **Controller**: 暴露 RESTful 接口，路径前缀规范（如 `/today`, `/plan`）。
- **Redis**: 聊天记录存储需注意过期时间（当前设定为 1 天）。
- **代码编辑**: 优先使用 `Edit` 工具进行精确替换，保持原有的缩进（通常是 4 空格）。

## 常用命令
- **编译**: `mvn clean compile`
- **打包**: `mvn clean package -DskipTests`
- **部署**: `./deploy.sh`
- **查看日志**: `ssh root@123.207.199.246 'docker logs -f aily-pec-app'`
