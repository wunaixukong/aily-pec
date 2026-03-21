-- ==========================================
-- Pomodoro config table
-- ==========================================
CREATE TABLE IF NOT EXISTS `pomodoro_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `config_name` varchar(50) DEFAULT '默认配置' COMMENT '配置名称',
  `work_duration` int NOT NULL DEFAULT 25 COMMENT '工作时长（分钟）',
  `break_duration` int NOT NULL DEFAULT 5 COMMENT '休息时长（分钟）',
  `start_time` time NOT NULL DEFAULT '09:00:00' COMMENT '生效开始时间',
  `end_time` time NOT NULL DEFAULT '18:00:00' COMMENT '生效结束时间',
  `auto_start` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否自动开始下一个周期',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否激活',
  `created_at` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime(6) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_user_active` (`user_id`, `is_active`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='番茄钟配置表';

-- ==========================================
-- Common migration helpers
-- ==========================================
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
    IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', col_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS drop_column_if_exists;
DELIMITER //
CREATE PROCEDURE drop_column_if_exists(
    IN tbl VARCHAR(64), IN col VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tbl, '` DROP COLUMN `', col, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS add_unique_index_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_unique_index_if_not_exists(
    IN tbl VARCHAR(64), IN idx VARCHAR(64), IN idx_cols VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tbl, '` ADD UNIQUE INDEX `', idx, '` ', idx_cols);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS drop_index_if_exists;
DELIMITER //
CREATE PROCEDURE drop_index_if_exists(
    IN tbl VARCHAR(64), IN idx VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tbl, '` DROP INDEX `', idx, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_column_if_not_exists('pomodoro_configs', 'config_name', "VARCHAR(50) DEFAULT '默认配置' COMMENT '配置名称'");
CALL add_column_if_not_exists('pomodoro_configs', 'break_duration', "INT NOT NULL DEFAULT 5 COMMENT '休息时长（分钟）'");
CALL add_column_if_not_exists('pomodoro_configs', 'start_time', "TIME NOT NULL DEFAULT '09:00:00' COMMENT '生效开始时间'");
CALL add_column_if_not_exists('pomodoro_configs', 'end_time', "TIME NOT NULL DEFAULT '18:00:00' COMMENT '生效结束时间'");

CALL drop_column_if_exists('pomodoro_configs', 'short_break_duration');
CALL drop_column_if_exists('pomodoro_configs', 'long_break_duration');
CALL drop_column_if_exists('pomodoro_configs', 'long_break_interval');

-- ==========================================
-- Workout records
-- ==========================================
CREATE TABLE IF NOT EXISTS `workout_records` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `plan_id` bigint NOT NULL COMMENT '训练计划ID',
  `workout_day_id` bigint DEFAULT NULL COMMENT '实际完成训练日ID',
  `content` varchar(500) DEFAULT NULL COMMENT '实际完成训练内容',
  `recommendation_id` bigint DEFAULT NULL COMMENT '今日推荐快照ID',
  `base_workout_day_id` bigint DEFAULT NULL COMMENT '原计划训练日ID',
  `completion_mode` varchar(50) DEFAULT NULL COMMENT '完成模式',
  `pointer_advanced` tinyint(1) DEFAULT NULL COMMENT '是否推进指针',
  `status_description_snapshot` varchar(1000) DEFAULT NULL COMMENT '状态描述快照',
  `recommendation_reason_snapshot` varchar(1000) DEFAULT NULL COMMENT '推荐原因快照',
  `recommended_workout_day_id` bigint DEFAULT NULL COMMENT '推荐训练日ID',
  `recommended_content` varchar(500) DEFAULT NULL COMMENT '推荐训练内容快照',
  `workout_date` date NOT NULL COMMENT '训练日期',
  `create_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_date` (`user_id`, `workout_date`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='训练记录表';

CALL add_column_if_not_exists('workout_records', 'recommendation_id', "BIGINT DEFAULT NULL COMMENT '今日推荐快照ID'");
CALL add_column_if_not_exists('workout_records', 'base_workout_day_id', "BIGINT DEFAULT NULL COMMENT '原计划训练日ID'");
CALL add_column_if_not_exists('workout_records', 'completion_mode', "VARCHAR(50) DEFAULT NULL COMMENT '完成模式'");
CALL add_column_if_not_exists('workout_records', 'pointer_advanced', "TINYINT(1) DEFAULT NULL COMMENT '是否推进指针'");
CALL add_column_if_not_exists('workout_records', 'status_description_snapshot', "VARCHAR(1000) DEFAULT NULL COMMENT '状态描述快照'");
CALL add_column_if_not_exists('workout_records', 'recommendation_reason_snapshot', "VARCHAR(1000) DEFAULT NULL COMMENT '推荐原因快照'");
CALL add_column_if_not_exists('workout_records', 'recommended_workout_day_id', "BIGINT DEFAULT NULL COMMENT '推荐训练日ID'");
CALL add_column_if_not_exists('workout_records', 'recommended_content', "VARCHAR(500) DEFAULT NULL COMMENT '推荐训练内容快照'");

ALTER TABLE `workout_records`
    MODIFY COLUMN `workout_day_id` bigint DEFAULT NULL COMMENT '实际完成训练日ID';

-- ==========================================
-- Today status
-- ==========================================
CREATE TABLE IF NOT EXISTS `today_statuses` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `status_date` date NOT NULL COMMENT '状态日期',
  `description` varchar(1000) NOT NULL COMMENT '状态描述',
  `create_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_status_date` (`user_id`, `status_date`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户今日状态表';

-- ==========================================
-- Today workout recommendations
-- ==========================================
CREATE TABLE IF NOT EXISTS `today_workout_recommendations` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `plan_id` bigint NOT NULL COMMENT '训练计划ID',
  `recommendation_date` date NOT NULL COMMENT '推荐日期',
  `base_workout_day_id` bigint NOT NULL COMMENT '原计划训练日ID',
  `base_content` varchar(500) NOT NULL COMMENT '原计划训练内容',
  `recommended_workout_day_id` bigint DEFAULT NULL COMMENT '推荐训练日ID',
  `recommended_content` varchar(500) NOT NULL COMMENT '推荐训练内容',
  `recommendation_type` varchar(50) NOT NULL COMMENT '推荐类型',
  `recommendation_reason` varchar(1000) DEFAULT NULL COMMENT '推荐原因',
  `status_description_snapshot` varchar(1000) DEFAULT NULL COMMENT '状态描述快照',
  `fallback_used` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否使用兜底推荐',
  `completed` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已完成训练',
  `create_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_recommendation_date` (`user_id`, `recommendation_date`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='今日训练推荐快照表';

-- ==========================================
-- Today workout chat sessions
-- ==========================================
CREATE TABLE IF NOT EXISTS `today_workout_chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `recommendation_id` bigint NOT NULL COMMENT '关联推荐ID',
  `content` text NOT NULL COMMENT '会话JSON',
  `create_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_recommendation_id` (`recommendation_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='今日训练会话表';

CALL drop_column_if_exists('today_workout_chat_messages', 'role');
CALL add_column_if_not_exists('today_workout_chat_messages', 'update_time', "DATETIME(6) DEFAULT NULL COMMENT '更新时间'");
CALL drop_index_if_exists('today_workout_chat_messages', 'idx_recommendation_id');
CALL add_unique_index_if_not_exists('today_workout_chat_messages', 'uk_recommendation_id', '(`recommendation_id`) USING BTREE');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS drop_column_if_exists;
DROP PROCEDURE IF EXISTS add_unique_index_if_not_exists;
DROP PROCEDURE IF EXISTS drop_index_if_exists;
