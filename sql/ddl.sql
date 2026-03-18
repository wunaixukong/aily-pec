-- ==========================================
-- 番茄钟配置表 DDL（幂等，可重复执行）
-- ==========================================

-- 建表（如果不存在）
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
  KEY `idx_user_id` (`user_id`) USING BTREE COMMENT '用户ID索引',
  KEY `idx_user_active` (`user_id`, `is_active`) USING BTREE COMMENT '用户激活状态索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='番茄钟配置表';

-- ==========================================
-- 迁移：安全地增删列（幂等，已存在/已删除则跳过）
-- ==========================================

-- 辅助存储过程：如果列不存在则添加
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

-- 辅助存储过程：如果列存在则删除
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

-- 新增列（幂等）
CALL add_column_if_not_exists('pomodoro_configs', 'config_name', "VARCHAR(50) DEFAULT '默认配置' COMMENT '配置名称'");
CALL add_column_if_not_exists('pomodoro_configs', 'break_duration', "INT NOT NULL DEFAULT 5 COMMENT '休息时长（分钟）'");
CALL add_column_if_not_exists('pomodoro_configs', 'start_time', "TIME NOT NULL DEFAULT '09:00:00' COMMENT '生效开始时间'");
CALL add_column_if_not_exists('pomodoro_configs', 'end_time', "TIME NOT NULL DEFAULT '18:00:00' COMMENT '生效结束时间'");

-- 删除旧列（幂等）
CALL drop_column_if_exists('pomodoro_configs', 'short_break_duration');
CALL drop_column_if_exists('pomodoro_configs', 'long_break_duration');
CALL drop_column_if_exists('pomodoro_configs', 'long_break_interval');

-- 清理辅助存储过程
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS drop_column_if_exists;

-- ==========================================
-- 索引（幂等，已存在则跳过）
-- ==========================================
-- MySQL CREATE INDEX IF NOT EXISTS 需要 8.0.29+，此处用存储过程兼容
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_index_if_not_exists(
    IN tbl VARCHAR(64), IN idx VARCHAR(64), IN idx_cols VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tbl, '` ADD KEY `', idx, '` (', idx_cols, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_index_if_not_exists('pomodoro_configs', 'idx_user_active_time', '`user_id`, `is_active`, `start_time`, `end_time`');

DROP PROCEDURE IF EXISTS add_index_if_not_exists;
