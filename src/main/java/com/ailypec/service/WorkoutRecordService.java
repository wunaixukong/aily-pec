package com.ailypec.service;

import com.ailypec.entity.WorkoutRecord;
import com.ailypec.repository.WorkoutRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 训练记录服务类
 */
@Service
@RequiredArgsConstructor
public class WorkoutRecordService {

    private final WorkoutRecordRepository workoutRecordRepository;

    /**
     * 获取用户所有训练记录（按日期倒序）
     *
     * @param userId 用户ID
     * @return 训练记录列表
     */
    @Transactional(readOnly = true)
    public List<WorkoutRecord> getRecordsByUserId(Long userId) {
        return workoutRecordRepository.findByUserIdOrderByWorkoutDateDesc(userId);
    }

    /**
     * 获取用户今天的训练记录
     *
     * @param userId 用户ID
     * @return 今天的训练记录列表
     */
    @Transactional(readOnly = true)
    public List<WorkoutRecord> getTodayRecords(Long userId) {
        return workoutRecordRepository.findByUserIdAndWorkoutDate(userId, LocalDate.now());
    }

    /**
     * 保存训练记录
     *
     * @param record 训练记录
     * @return 保存后的记录
     */
    @Transactional
    public WorkoutRecord saveRecord(WorkoutRecord record) {
        if (record.getWorkoutDate() == null) {
            record.setWorkoutDate(LocalDate.now());
        }
        return workoutRecordRepository.save(record);
    }
}
