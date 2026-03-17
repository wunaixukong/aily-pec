package com.ailypec.service;

import com.ailypec.config.PomodoroConfigProperties;
import com.ailypec.entity.PomodoroConfig;
import com.ailypec.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 番茄钟配置内存服务类
 * 使用HashMap存储配置，支持工作日默认配置
 * 后续可迁移到Redis
 */
@Service
@RequiredArgsConstructor
public class PomodoroConfigMemoryService {

    /**
     * 用户配置存储
     * Key: userId, Value: PomodoroConfig
     */
    private final Map<Long, PomodoroConfig> userConfigs = new ConcurrentHashMap<>();

    /**
     * 配置属性
     */
    private final PomodoroConfigProperties configProperties;

    /**
     * 创建或更新番茄钟配置
     *
     * @param config 配置信息
     * @return 创建的配置
     */
    public PomodoroConfig createOrUpdateConfig(PomodoroConfig config) {
        // 设置默认值
        if (config.getWorkDuration() == null) {
            config.setWorkDuration(configProperties.getDefaultConfig().getWorkDuration());
        }
        if (config.getShortBreakDuration() == null) {
            config.setShortBreakDuration(configProperties.getDefaultConfig().getShortBreakDuration());
        }
        if (config.getLongBreakDuration() == null) {
            config.setLongBreakDuration(configProperties.getDefaultConfig().getLongBreakDuration());
        }
        if (config.getLongBreakInterval() == null) {
            config.setLongBreakInterval(configProperties.getDefaultConfig().getLongBreakInterval());
        }
        if (config.getAutoStart() == null) {
            config.setAutoStart(configProperties.getDefaultConfig().isAutoStart());
        }
        if (config.getIsActive() == null) {
            config.setIsActive(true);
        }

        // 设置ID（使用用户ID作为配置ID）
        config.setId(config.getUserId());

        // 存储配置
        userConfigs.put(config.getUserId(), config);
        return config;
    }

    /**
     * 根据用户ID获取配置
     * 如果没有配置，返回工作日默认配置
     *
     * @param userId 用户ID
     * @return 配置（可选）
     */
    public Optional<PomodoroConfig> getConfigByUserId(Long userId) {
        PomodoroConfig config = userConfigs.get(userId);

        if (config == null) {
            // 返回工作日默认配置
            config = getDefaultWorkdayConfig(userId);
        }

        return Optional.of(config);
    }

    /**
     * 根据用户ID获取激活的配置
     *
     * @param userId 用户ID
     * @return 激活的配置（可选）
     */
    public Optional<PomodoroConfig> getActiveConfigByUserId(Long userId) {
        return getConfigByUserId(userId);
    }

    /**
     * 删除用户配置
     *
     * @param userId 用户ID
     */
    public void deleteConfig(Long userId) {
        userConfigs.remove(userId);
    }

    /**
     * 获取所有用户配置
     *
     * @return 所有配置
     */
    public Map<Long, PomodoroConfig> getAllConfigs() {
        return new ConcurrentHashMap<>(userConfigs);
    }

    /**
     * 清除所有配置
     */
    public void clearAllConfigs() {
        userConfigs.clear();
    }

    /**
     * 获取工作日默认配置
     * 工作时间：每小时工作50分钟，休息10分钟
     *
     * @param userId 用户ID
     * @return 默认配置
     */
    private PomodoroConfig getDefaultWorkdayConfig(Long userId) {
        PomodoroConfig defaultConfig = new PomodoroConfig();
        defaultConfig.setId(userId);
        defaultConfig.setUserId(userId);

        // 检查是否是工作日且在工作时间内
        if (isWorkTime()) {
            // 工作时间配置：50分钟工作，10分钟休息
            PomodoroConfigProperties.WorkdayConfig workday = configProperties.getWorkdayConfig();
            defaultConfig.setWorkDuration(workday.getWorkDuration());
            defaultConfig.setShortBreakDuration(workday.getShortBreakDuration());
            defaultConfig.setLongBreakDuration(workday.getLongBreakDuration());
            defaultConfig.setLongBreakInterval(workday.getLongBreakInterval());
            defaultConfig.setAutoStart(workday.isAutoStart());
            defaultConfig.setIsActive(true);
        } else {
            // 非工作时间配置：标准番茄钟
            PomodoroConfigProperties.DefaultConfig defaultProps = configProperties.getDefaultConfig();
            defaultConfig.setWorkDuration(defaultProps.getWorkDuration());
            defaultConfig.setShortBreakDuration(defaultProps.getShortBreakDuration());
            defaultConfig.setLongBreakDuration(defaultProps.getLongBreakDuration());
            defaultConfig.setLongBreakInterval(defaultProps.getLongBreakInterval());
            defaultConfig.setAutoStart(defaultProps.isAutoStart());
            defaultConfig.setIsActive(true);
        }

        return defaultConfig;
    }

    /**
     * 检查当前是否在工作时间
     * 工作日：配置的工作时间
     *
     * @return 是否在工作时间
     */
    private boolean isWorkTime() {
        if (!configProperties.getWorkTime().isEnabled()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        // 检查是否是工作日（周一到周五）
        boolean isWorkday = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;

        // 检查是否在工作时间内（配置的工作时间）
        PomodoroConfigProperties.WorkTimeConfig workTime = configProperties.getWorkTime();
        boolean isWorkHours = !time.isBefore(LocalTime.of(workTime.getStartHour(), 0)) &&
                             !time.isAfter(LocalTime.of(workTime.getEndHour(), 0));

        return isWorkday && isWorkHours;
    }

}