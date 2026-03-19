package com.ailypec.repository;

import com.ailypec.entity.WorkoutRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 训练记录数据访问层
 */
@Repository
public interface WorkoutRecordRepository extends JpaRepository<WorkoutRecord, Long> {

    /**
     * 查询用户在指定日期的训练记录
     * 用于判断用户当天是否已完成训练
     *
     * @param userId      用户ID
     * @param workoutDate 训练日期
     * @return 训练记录列表
     */
    List<WorkoutRecord> findByUserIdAndWorkoutDate(Long userId, LocalDate workoutDate);

    /**
     * 查询用户的所有训练记录
     *
     * @param userId 用户ID
     * @return 训练记录列表
     */
    List<WorkoutRecord> findByUserIdOrderByWorkoutDateDesc(Long userId);

}
