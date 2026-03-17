package com.ailypec.controller;

import com.ailypec.entity.PomodoroConfig;
import com.ailypec.response.Result;
import com.ailypec.service.PomodoroConfigMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 番茄钟配置内存控制器
 * 使用HashMap内存存储，支持工作日默认配置
 */
@Slf4j
@RestController
@RequestMapping("/api/pomodoro/memory")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PomodoroConfigMemoryController {

    private final PomodoroConfigMemoryService pomodoroConfigMemoryService;

    /**
     * 创建或更新番茄钟配置
     *
     * @param config 配置信息
     * @return 创建的配置
     */
    @PostMapping("/config")
    public ResponseEntity<PomodoroConfig> createOrUpdateConfig(@RequestBody PomodoroConfig config) {
        PomodoroConfig created = pomodoroConfigMemoryService.createOrUpdateConfig(config);
        log.info("Created/Updated pomodoro config for user {}: {}", config.getUserId(), created.getWorkDuration());
        return ResponseEntity.ok(created);
    }

    /**
     * 获取用户的配置
     * 如果没有配置，返回工作日默认配置
     *
     * @param userId 用户ID
     * @return 配置信息
     */
    @GetMapping("/config/{userId}")
    public ResponseEntity<PomodoroConfig> getConfig(@PathVariable Long userId) {
        return pomodoroConfigMemoryService.getConfigByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取用户的激活配置
     *
     * @param userId 用户ID
     * @return 激活的配置
     */
    @GetMapping("/config/active/{userId}")
    public ResponseEntity<PomodoroConfig> getActiveConfig(@PathVariable Long userId) {
        return pomodoroConfigMemoryService.getActiveConfigByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除用户配置
     *
     * @param userId 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/config/{userId}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long userId) {
        pomodoroConfigMemoryService.deleteConfig(userId);
        log.info("Deleted pomodoro config for user: {}", userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取所有用户配置（仅用于调试）
     *
     * @return 所有配置
     */
    @GetMapping("/configs")
    public ResponseEntity<Map<Long, PomodoroConfig>> getAllConfigs() {
        Map<Long, PomodoroConfig> allConfigs = pomodoroConfigMemoryService.getAllConfigs();
        return ResponseEntity.ok(allConfigs);
    }

    /**
     * 清除所有配置（仅用于调试）
     *
     * @return 清除结果
     */
    @DeleteMapping("/configs")
    public Result<String> clearAllConfigs() {
        pomodoroConfigMemoryService.clearAllConfigs();
        log.info("Cleared all pomodoro configs");
        return Result.success("All configs cleared");
    }

}