package com.ailypec.service;

import com.ailypec.entity.PomodoroConfig;
import com.ailypec.repository.PomodoroConfigRepository;
import com.ailypec.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 番茄钟配置服务类
 * 处理番茄钟配置的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class PomodoroConfigService {

    private final PomodoroConfigRepository pomodoroConfigRepository;

    /**
     * 创建番茄钟配置
     *
     * @param config 配置信息
     * @return 创建的配置
     */
    @Transactional
    public PomodoroConfig createConfig(PomodoroConfig config) {
        // 如果设置为激活状态，先停用该用户的其他配置
        if (Boolean.TRUE.equals(config.getIsActive())) {
            deactivateUserConfigs(config.getUserId());
        }
        return pomodoroConfigRepository.save(config);
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

            // 如果设置为激活状态，先停用该用户的其他配置
            if (Boolean.TRUE.equals(config.getIsActive()) && !config.getIsActive().equals(existing.getIsActive())) {
                deactivateUserConfigs(existing.getUserId());
            }

            // 更新配置字段
            existing.setWorkDuration(config.getWorkDuration());
            existing.setShortBreakDuration(config.getShortBreakDuration());
            existing.setLongBreakDuration(config.getLongBreakDuration());
            existing.setLongBreakInterval(config.getLongBreakInterval());
            existing.setAutoStart(config.getAutoStart());
            existing.setIsActive(config.getIsActive());

            PomodoroConfig updated = pomodoroConfigRepository.save(existing);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.fail("更新配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户ID获取激活的配置
     *
     * @param userId 用户ID
     * @return 激活的配置（可选）
     */
    @Transactional(readOnly = true)
    public Optional<PomodoroConfig> getActiveConfigByUserId(Long userId) {
        return pomodoroConfigRepository.findFirstByUserIdAndIsActive(userId, true);
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
     * 停用用户的所有配置
     *
     * @param userId 用户ID
     */
    @Transactional
    private void deactivateUserConfigs(Long userId) {
        List<PomodoroConfig> activeConfigs = pomodoroConfigRepository.findByUserIdAndIsActive(userId, true);
        for (PomodoroConfig config : activeConfigs) {
            config.setIsActive(false);
        }
        pomodoroConfigRepository.saveAll(activeConfigs);
    }

}