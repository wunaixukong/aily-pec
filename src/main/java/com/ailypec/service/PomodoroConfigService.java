package com.ailypec.service;

import com.ailypec.entity.PomodoroConfig;
import com.ailypec.repository.PomodoroConfigRepository;
import com.ailypec.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 番茄钟配置服务类
 * 处理番茄钟配置的CRUD操作，支持多时间段配置
 */
@Service
@RequiredArgsConstructor
public class PomodoroConfigService {

    private final PomodoroConfigRepository pomodoroConfigRepository;

    /**
     * 创建番茄钟配置
     * 包含时间段校验和重叠检测
     *
     * @param config 配置信息
     * @return 创建结果
     */
    @Transactional
    public Result<PomodoroConfig> createConfig(PomodoroConfig config) {
        // 校验配置
        String validationError = validateConfig(config, null);
        if (validationError != null) {
            return Result.fail(validationError);
        }

        PomodoroConfig saved = pomodoroConfigRepository.save(config);
        return Result.success(saved);
    }

    /**
     * 更新番茄钟配置
     *
     * @param config 配置信息
     * @return 更新结果
     */
    @Transactional
    public Result<PomodoroConfig> updateConfig(PomodoroConfig config) {
        try {
            // 检查配置是否存在
            Optional<PomodoroConfig> existingConfig = pomodoroConfigRepository.findById(config.getId());
            if (existingConfig.isEmpty()) {
                return Result.fail("配置不存在");
            }

            PomodoroConfig existing = existingConfig.get();

            // 校验配置（排除自身进行重叠检测）
            String validationError = validateConfig(config, existing.getId());
            if (validationError != null) {
                return Result.fail(validationError);
            }

            // 更新配置字段
            existing.setConfigName(config.getConfigName());
            existing.setWorkDuration(config.getWorkDuration());
            existing.setBreakDuration(config.getBreakDuration());
            existing.setStartTime(config.getStartTime());
            existing.setEndTime(config.getEndTime());
            existing.setAutoStart(config.getAutoStart());
            existing.setIsActive(config.getIsActive());

            PomodoroConfig updated = pomodoroConfigRepository.save(existing);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.fail("更新配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据当前时间获取用户生效的配置
     *
     * @param userId 用户ID
     * @return 当前时间命中的配置（可选）
     */
    @Transactional(readOnly = true)
    public Optional<PomodoroConfig> getCurrentConfig(Long userId) {
        LocalTime now = LocalTime.now();
        return pomodoroConfigRepository.findActiveConfigByUserIdAndTime(userId, now);
    }

    /**
     * 获取用户所有激活配置列表，按开始时间排序
     *
     * @param userId 用户ID
     * @return 激活配置列表
     */
    @Transactional(readOnly = true)
    public List<PomodoroConfig> getActiveConfigsByUserId(Long userId) {
        return pomodoroConfigRepository.findByUserIdAndIsActiveOrderByStartTimeAsc(userId, true);
    }

    /**
     * 根据用户ID获取激活的配置（向后兼容）
     * 改为调用 getCurrentConfig
     *
     * @param userId 用户ID
     * @return 当前时间生效的配置（可选）
     */
    @Transactional(readOnly = true)
    public Optional<PomodoroConfig> getActiveConfigByUserId(Long userId) {
        return getCurrentConfig(userId);
    }

    /**
     * 根据用户ID获取所有配置
     *
     * @param userId 用户ID
     * @return 配置列表
     */
    @Transactional(readOnly = true)
    public List<PomodoroConfig> getConfigsByUserId(Long userId) {
        return pomodoroConfigRepository.findByUserId(userId);
    }

    /**
     * 根据配置ID获取配置
     *
     * @param id 配置ID
     * @return 配置（可选）
     */
    @Transactional(readOnly = true)
    public Optional<PomodoroConfig> getConfigById(Long id) {
        return pomodoroConfigRepository.findById(id);
    }

    /**
     * 删除配置
     *
     * @param id 配置ID
     */
    @Transactional
    public void deleteConfig(Long id) {
        pomodoroConfigRepository.deleteById(id);
    }

    /**
     * 统一校验配置
     * - startTime 必须小于 endTime（不支持跨午夜）
     * - workDuration > 0
     * - breakDuration > 0
     * - 同一用户的激活配置时间段不可重叠（相邻可以）
     *
     * @param config 待校验的配置
     * @param excludeId 排除的配置ID（更新时排除自身），新建时传 null
     * @return 校验错误信息，校验通过返回 null
     */
    private String validateConfig(PomodoroConfig config, Long excludeId) {
        // 校验工作时长
        if (config.getWorkDuration() == null || config.getWorkDuration() <= 0) {
            return "工作时长必须大于0";
        }

        // 校验休息时长
        if (config.getBreakDuration() == null || config.getBreakDuration() <= 0) {
            return "休息时长必须大于0";
        }

        // 校验时间段
        if (config.getStartTime() == null || config.getEndTime() == null) {
            return "开始时间和结束时间不能为空";
        }

        if (!config.getStartTime().isBefore(config.getEndTime())) {
            return "开始时间必须早于结束时间（暂不支持跨午夜）";
        }

        // 仅对激活配置检测重叠
        if (Boolean.TRUE.equals(config.getIsActive())) {
            List<PomodoroConfig> overlapping = pomodoroConfigRepository.findOverlappingConfigs(
                    config.getUserId(),
                    config.getStartTime(),
                    config.getEndTime(),
                    excludeId);

            if (!overlapping.isEmpty()) {
                PomodoroConfig first = overlapping.get(0);
                return String.format("时间段与已有配置「%s」(%s-%s) 重叠",
                        first.getConfigName(),
                        first.getStartTime(),
                        first.getEndTime());
            }
        }

        return null;
    }

}
