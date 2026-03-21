# aily-pec

## 项目介绍

项目名的由来:aily-pec = Daily (每日) + Love (爱练) + Pecs (强壮体魄)。

合起来的意思就是：因为热爱，所以每日坚持，最终练就强健的体魄。 这个名字读起来既有“爱练”的谐音亲切感，结尾的“pec”又给它增添了一份专业健身房的硬核气息。

aily-pec 是一个面向个人健身与训练节奏管理的后端项目，核心目标是帮助用户围绕“训练计划、今日训练、训练记录、番茄钟专注配置”形成一套可持续执行的日常管理流程。

从当前后端接口来看，这个项目的主要用途包括：

1. **管理健身用户信息**
   - 支持创建用户、查询全部用户、按 ID 查询用户。
   - 对应接口：`/api/users`

2. **管理训练计划**
   - 用户可以创建训练计划，并为计划配置多个训练日内容。
   - 支持查询用户所有计划、查询当前激活计划、激活指定计划、编辑计划、删除计划。
   - 对应接口：`/api/plans`

3. **生成和推进“今日训练”**
   - 系统会基于用户当前激活的训练计划和进度指针，计算今天应该执行哪一天的训练内容。
   - 当用户完成今日训练后，系统会自动推进训练进度，并返回下一次应执行的训练内容。
   - 同时会限制同一用户同一天不能重复完成训练。
   - 对应接口：`/api/today`

4. **记录训练完成情况**
   - 支持保存训练记录、查询用户全部训练记录、查询用户当天训练记录。
   - 训练记录会关联用户、计划、训练日和训练日期，可用于后续训练历史回顾和进度统计。
   - 对应接口：`/api/record`

5. **管理番茄钟配置**
   - 项目除了健身训练外，还集成了番茄钟模块，用于管理用户的专注/休息节奏。
   - 支持创建、更新、查询、删除番茄钟配置。
   - 支持按用户和当前时间命中生效配置，且对时间段重叠、时长有效性做了校验。
   - 对应接口：`/api/pomodoro`

6. **提供内存版番茄钟配置能力**
   - 除数据库版番茄钟外，还提供了基于内存 `HashMap` 的配置实现。
   - 当用户没有自定义配置时，系统会根据“是否处于工作日和工作时间”自动返回默认配置，用于快速体验或轻量运行。
   - 对应接口：`/api/pomodoro/memory`

综合来看，当前项目本质上是一个：

> **面向个人用户的健身训练管理后端系统，同时融合了番茄钟专注配置能力，用于支持训练计划制定、每日训练推进、训练记录沉淀，以及训练/专注节奏管理。**

它既可以作为一个 AI 健身助手的后端雏形，也适合作为“个人自律管理”类应用的服务端基础。

## 当前后端核心业务链路

### 1. 用户进入系统
先创建用户信息，作为后续训练计划、训练记录、番茄钟配置的归属主体。

### 2. 创建训练计划
用户创建自己的训练计划，计划中包含多个训练日（WorkoutDay），例如按顺序配置不同天的训练内容。

### 3. 激活某个训练计划
系统支持一个用户在某一时刻选择一个激活计划。激活后，今日训练会基于该计划推进。

### 4. 查询今日训练内容
系统根据当前激活计划和进度指针（ProgressPointer），返回今天应该完成的训练内容。

### 5. 完成今日训练
用户完成后调用完成接口：
- 校验今天是否已经完成过训练
- 写入训练记录
- 推进训练指针到下一天
- 返回下一次训练内容

### 6. 配置专注节奏
用户可根据不同时段设置番茄钟工作/休息时长，用于配合训练、自律或日常专注安排。

## 主要接口模块

### 用户模块
- `POST /api/users`：创建用户
- `GET /api/users`：查询所有用户
- `GET /api/users/{id}`：按 ID 查询用户

### 训练计划模块
- `POST /api/plans`：创建训练计划
- `GET /api/plans/user/{userId}`：查询用户所有训练计划
- `GET /api/plans/active/{userId}`：查询当前激活计划
- `PUT /api/plans/{planId}/activate?userId={userId}`：激活训练计划
- `POST /api/plans/edit`：编辑训练计划
- `DELETE /api/plans/{planId}`：删除训练计划

### 今日训练模块
- `GET /api/today/{userId}`：获取今日训练内容
- `POST /api/today/{userId}/complete`：完成今日训练并推进进度

### 训练记录模块
- `GET /api/record/list/{userId}`：查询全部训练记录
- `GET /api/record/today/{userId}`：查询今日训练记录
- `POST /api/record/save`：保存训练记录

### 番茄钟模块（数据库版）
- `POST /api/pomodoro/config`：创建配置
- `PUT /api/pomodoro/config/{id}`：更新配置
- `GET /api/pomodoro/config/current/{userId}`：获取当前时间生效配置
- `GET /api/pomodoro/config/active-list/{userId}`：获取激活配置列表
- `GET /api/pomodoro/config/active/{userId}`：获取当前激活配置
- `GET /api/pomodoro/config/user/{userId}`：获取用户全部配置
- `GET /api/pomodoro/config/{id}`：按 ID 获取配置
- `DELETE /api/pomodoro/config/{id}`：删除配置

### 番茄钟模块（内存版）
- `POST /api/pomodoro/memory/config`：创建或更新配置
- `GET /api/pomodoro/memory/config/{userId}`：获取用户配置
- `GET /api/pomodoro/memory/config/active/{userId}`：获取激活配置
- `DELETE /api/pomodoro/memory/config/{userId}`：删除用户配置
- `GET /api/pomodoro/memory/configs`：查看所有内存配置（调试）
- `DELETE /api/pomodoro/memory/configs`：清空所有内存配置（调试）

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Spring Web
- Spring Data JPA
- MySQL
- Lombok

## 数据存储说明

当前项目至少包含以下核心数据：
- 用户信息
- 训练计划
- 训练日内容
- 训练进度指针
- 训练记录
- 番茄钟配置

其中：
- `pomodoro_configs` 表用于保存番茄钟配置
- `workout_records` 表用于保存训练记录

## 适用场景

这个项目适合以下方向：
- 个人健身计划管理应用后端
- AI 健身助手后端服务
- 训练打卡与进度推进系统
- 健身 + 专注管理结合类产品
- 个人自律/习惯养成类应用的服务端基础
