# AI 智能分化训练助手 - 项目进度

## 一、项目目标

解决健身者因突发状况断练导致的"进度重置"痛点，通过队列式分化逻辑与 AI 动态调整，实现真正智能的进度管理。

---

## 二、技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17 + Spring Boot 3.x |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis（待添加） |
| AI 集成 | Spring AI + DeepSeek API（待添加） |
| 移动端 | Flutter 3.x（待开发） |

---

## 三、功能模块进度

### 3.1 用户系统 ✅ 已完成

- [x] 用户实体设计（User）
- [x] 用户注册/创建接口
- [x] 用户查询接口（按ID、查询全部）
- [x] MySQL 数据持久化
- [x] Lombok 简化代码

**相关文件：**
- `entity/User.java`
- `repository/UserRepository.java`
- `service/UserService.java`
- `controller/UserController.java`

---

### 3.2 训练引擎（Training Engine）⏳ 待开发

#### 3.2.1 分化计划管理
- [ ] 创建分化计划（PPL、五分化、上下肢等）
- [ ] 计划包含多天训练日
- [ ] 每天包含多个动作序列

#### 3.2.2 队列式进度指针（核心功能）
- [ ] 非日期绑定的 Pointer 模式
- [ ] 记录用户当前执行到计划的第几天
- [ ] 完成训练后自动推进到下一项（循环）
- [ ] 示例：计划为 [胸, 背, 肩, 腿]，周一练完胸，周二周三加班，周四打开依然指向"背"

**待创建实体：**
- `WorkoutPlan` - 训练计划
- `WorkoutDay` - 训练日
- `Exercise` - 动作
- `ProgressPointer` - 进度指针

---

### 3.3 AI 教练助手（AI Coach）⏳ 待开发

- [ ] 状态评估：每日训练前输入疲劳度（1-10）和酸痛部位
- [ ] 智能微调：AI 根据队列进度 + 身体状态，给出今日动作微调建议
- [ ] 动作平替：针对器械被占用的情况，AI 提供生物力学相似的替代动作

**依赖：**
- 接入 DeepSeek API
- Spring AI 集成

---

### 3.4 数据追踪 ⏳ 待开发

- [ ] 容量记录：记录组数、次数、重量
- [ ] 训练日志：每次训练的完整记录
- [ ] 统计图表：肌肉部位覆盖率饼图、容量增长曲线

**待创建实体：**
- `WorkoutLog` - 训练日志
- `ExerciseLog` - 动作日志
- `SetLog` - 每组记录

---

### 3.5 缓存层（Redis）⏳ 待添加

- [ ] Redis 配置
- [ ] 进度指针缓存
- [ ] 热点数据缓存

---

## 四、数据库表结构

### 4.1 已创建

```sql
-- 用户表
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `nickname` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_username` (`username`),
  UNIQUE KEY `UK_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 4.2 待创建

- `workout_plans` - 训练计划表
- `workout_days` - 训练日表
- `exercises` - 动作表
- `progress_pointers` - 进度指针表
- `workout_logs` - 训练日志表
- `exercise_logs` - 动作日志表
- `set_logs` - 每组记录表

---

## 五、API 接口清单

### 5.1 用户接口 ✅ 已完成

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/users | 创建用户 |
| GET | /api/users | 查询所有用户 |
| GET | /api/users/{id} | 根据ID查询用户 |

### 5.2 计划接口 ⏳ 待开发

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/plans | 创建训练计划 |
| GET | /api/plans/user/{userId} | 获取用户的所有计划 |
| GET | /api/plans/{id} | 获取计划详情 |
| PUT | /api/plans/{id} | 更新计划 |
| DELETE | /api/plans/{id} | 删除计划 |

### 5.3 进度接口 ⏳ 待开发

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/progress/init/{userId} | 初始化进度指针 |
| GET | /api/progress/{userId} | 获取当前进度 |
| POST | /api/progress/advance/{userId} | 完成当前训练，推进到下一项 |

### 5.4 AI 接口 ⏳ 待开发

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/ai/training-tip | 获取今日训练建议 |
| POST | /api/ai/exercise-alternative | 获取动作替代建议 |
| POST | /api/ai/analyze | 分析训练数据 |

### 5.5 日志接口 ⏳ 待开发

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/logs | 记录训练日志 |
| GET | /api/logs/user/{userId} | 获取用户训练历史 |
| GET | /api/logs/stats | 获取统计数据 |

---

## 六、下一步计划

### 选项 A：继续后端开发
1. 创建训练计划相关实体和接口
2. 实现进度指针核心逻辑
3. 添加 Redis 缓存
4. 接入 DeepSeek AI

### 选项 B：开始前端开发
1. 搭建 Flutter 开发环境
2. 设计移动端页面原型
3. 对接后端接口

### 选项 C：完善基础设施
1. 添加 JWT 认证
2. 统一异常处理
3. 接口文档（Swagger）
4. 单元测试

---

## 七、项目结构

```
aily-pec/
├── src/main/java/com/ailypec/
│   ├── AilyPecApplication.java    # 启动类
│   ├── controller/                # 控制器层
│   │   ├── HelloController.java   # Hello World（测试用）
│   │   └── UserController.java    # 用户接口 ✅
│   ├── service/                   # 业务层
│   │   └── UserService.java       # 用户服务 ✅
│   ├── repository/                # 数据访问层
│   │   └── UserRepository.java    # 用户仓库 ✅
│   └── entity/                    # 实体层
│       └── User.java              # 用户实体 ✅
├── src/main/resources/
│   └── application.yml            # 配置文件
├── pom.xml                        # Maven配置
└── project-progress.md            # 本文件
```

---

## 八、更新记录

| 日期 | 更新内容 |
|------|----------|
| 2026-03-06 | 项目初始化，完成 Hello World |
| 2026-03-06 | 添加 Lombok + MySQL + JPA，完成用户模块 |

---

## 九、待决策事项

1. **多套计划支持**：用户是否需要同时开启多套计划（如一套力量，一套减脂）？
2. **动作库来源**：自己录入动作名称，还是使用开源健身动作数据库？
3. **离线需求**：健身房信号不好时，是否需要支持离线记录，有网后同步？
