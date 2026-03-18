package com.ailypec.controller;

import com.ailypec.entity.WorkoutRecord;
import com.ailypec.response.Result;
import com.ailypec.service.WorkoutRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 训练记录控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/record")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkoutRecordController {

    private final WorkoutRecordService workoutRecordService;

    /**
     * 获取用户所有训练记录列表
     */
    @GetMapping("/list/{userId}")
    public ResponseEntity<List<WorkoutRecord>> getRecords(@PathVariable Long userId) {
        List<WorkoutRecord> records = workoutRecordService.getRecordsByUserId(userId);
        return ResponseEntity.ok(records);
    }

    /**
     * 获取用户今天的训练记录
     */
    @GetMapping("/today/{userId}")
    public ResponseEntity<List<WorkoutRecord>> getTodayRecords(@PathVariable Long userId) {
        List<WorkoutRecord> records = workoutRecordService.getTodayRecords(userId);
        return ResponseEntity.ok(records);
    }

    /**
     * 保存训练记录
     */
    @PostMapping("/save")
    public Result<WorkoutRecord> saveRecord(@RequestBody WorkoutRecord record) {
        try {
            WorkoutRecord saved = workoutRecordService.saveRecord(record);
            log.info("Saved workout record for user {}: {}", record.getUserId(), saved.getId());
            return Result.success(saved);
        } catch (Exception e) {
            log.error("Failed to save workout record: {}", e.getMessage());
            return Result.fail("保存训练记录失败: " + e.getMessage());
        }
    }
}
