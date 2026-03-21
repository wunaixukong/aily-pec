package com.ailypec.controller;

import com.ailypec.entity.PomodoroConfig;
import com.ailypec.response.Result;
import com.ailypec.service.PomodoroConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 番茄钟配置控制器
 * 提供番茄钟配置的REST API接口
 * 支持多时间段配置管理
 */
@Slf4j
@RestController
@RequestMapping("/pomodoro")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PomodoroConfigController {

    private final PomodoroConfigService pomodoroConfigService;

    /**
     * 创建番茄钟配置
     *
     * @param config 配置信息
     * @return 创建结果
     */
    @PostMapping("/config")
    public Result<PomodoroConfig> createConfig(@RequestBody PomodoroConfig config) {
        Result<PomodoroConfig> result = pomodoroConfigService.createConfig(config);
        if (result.isSuccess()) {
            log.info("Created pomodoro config for user {}: {}", config.getUserId(), result.getData().getId());
        } else {
            log.warn("Failed to create pomodoro config for user {}: {}", config.getUserId(), result.getMessage());
        }
        return result;
    }

    /**
     * 更新番茄钟配置
     *
     * @param id 配置ID
     * @param config 配置信息
     * @return 更新结果
     */
    @PutMapping("/config/{id}")
    public Result<PomodoroConfig> updateConfig(@PathVariable Long id, @RequestBody PomodoroConfig config) {
        // 确保ID一致
        config.setId(id);
        Result<PomodoroConfig> result = pomodoroConfigService.updateConfig(config);
        if (result.isSuccess()) {
            log.info("Updated pomodoro config: {}", id);
        } else {
            log.warn("Failed to update pomodoro config {}: {}", id, result.getMessage());
        }
        return result;
    }

    /**
     * 获取当前时间生效的配置
     *
     * @param userId 用户ID
     * @return 当前时间命中的配置
     */
    @GetMapping("/config/current/{userId}")
    public ResponseEntity<PomodoroConfig> getCurrentConfig(@PathVariable Long userId) {
        return pomodoroConfigService.getCurrentConfig(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取用户所有激活配置列表
     *
     * @param userId 用户ID
     * @return 激活配置列表（按开始时间排序）
     */
    @GetMapping("/config/active-list/{userId}")
    public ResponseEntity<List<PomodoroConfig>> getActiveConfigs(@PathVariable Long userId) {
        List<PomodoroConfig> configs = pomodoroConfigService.getActiveConfigsByUserId(userId);
        return ResponseEntity.ok(configs);
    }

    /**
     * 获取用户的激活配置（向后兼容）
     * 改为返回当前时间生效的配置
     *
     * @param userId 用户ID
     * @return 当前时间生效的配置
     */
    @GetMapping("/config/active/{userId}")
    public ResponseEntity<PomodoroConfig> getActiveConfig(@PathVariable Long userId) {
        return pomodoroConfigService.getCurrentConfig(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取用户的所有配置
     *
     * @param userId 用户ID
     * @return 配置列表
     */
    @GetMapping("/config/user/{userId}")
    public ResponseEntity<List<PomodoroConfig>> getUserConfigs(@PathVariable Long userId) {
        List<PomodoroConfig> configs = pomodoroConfigService.getConfigsByUserId(userId);
        return ResponseEntity.ok(configs);
    }

    /**
     * 根据ID获取配置
     *
     * @param id 配置ID
     * @return 配置信息
     */
    @GetMapping("/config/{id}")
    public ResponseEntity<PomodoroConfig> getConfigById(@PathVariable Long id) {
        return pomodoroConfigService.getConfigById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除配置
     *
     * @param id 配置ID
     * @return 删除结果
     */
    @DeleteMapping("/config/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        pomodoroConfigService.deleteConfig(id);
        log.info("Deleted pomodoro config: {}", id);
        return ResponseEntity.ok().build();
    }

}
