-- 番茄钟配置表
-- 用于存储用户的番茄钟时间配置

CREATE TABLE `pomodoro_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `work_duration` int NOT NULL DEFAULT 25 COMMENT '工作时长（分钟）',
  `short_break_duration` int NOT NULL DEFAULT 5 COMMENT '短休息时长（分钟）',
  `long_break_duration` int NOT NULL DEFAULT 15 COMMENT '长休息时长（分钟）',
  `long_break_interval` int NOT NULL DEFAULT 4 COMMENT '长休息间隔（完成几个工作周期后长休息）',
  `auto_start` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否自动开始下一个周期',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否激活',
  `created_at` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime(6) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE COMMENT '用户ID索引',
  KEY `idx_user_active` (`user_id`, `is_active`) USING BTREE COMMENT '用户激活状态索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='番茄钟配置表';

-- 插入示例数据
INSERT INTO `pomodoro_configs` (
  `user_id`, `work_duration`, `short_break_duration`,
  `long_break_duration`, `long_break_interval`,
  `auto_start`, `is_active`
) VALUES
(1, 25, 5, 15, 4, 0, 1),
(1, 30, 10, 20, 3, 1, 0),
(2, 20, 5, 10, 4, 0, 1);

-- 查询所有配置
SELECT * FROM `pomodoro_configs`;

-- 查询特定用户的激活配置
SELECT * FROM `pomodoro_configs`
WHERE `user_id` = 1 AND `is_active` = 1;

-- 查询用户的所有配置
SELECT * FROM `pomodoro_configs`
WHERE `user_id` = 1
ORDER BY `created_at` DESC;

-- 更新配置状态
UPDATE `pomodoro_configs`
SET `is_active` = 0
WHERE `user_id` = 1 AND `id` != 1;

-- 删除配置
DELETE FROM `pomodoro_configs`
WHERE `id` = 3;