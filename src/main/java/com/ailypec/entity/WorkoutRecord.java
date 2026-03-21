package com.ailypec.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 训练记录实体类
 * 用于记录用户每次完成训练的历史数据
 */
@Entity
@Table(name = "workout_records")
@Data
public class WorkoutRecord {

    /**
     * 记录ID，主键自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID，关联用户表
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 训练计划ID，关联训练计划表
     */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /**
     * 训练日ID，关联训练日表
     */
    @Column(name = "workout_day_id")
    private Long workoutDayId;

    /**
     * 训练内容，冗余存储防止计划修改后丢失历史
     */
    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "recommendation_id")
    private Long recommendationId;

    @Column(name = "base_workout_day_id")
    private Long baseWorkoutDayId;

    @Column(name = "completion_mode", length = 50)
    private String completionMode;

    @Column(name = "pointer_advanced")
    private Boolean pointerAdvanced;

    @Column(name = "status_description_snapshot", length = 1000)
    private String statusDescriptionSnapshot;

    @Column(name = "recommendation_reason_snapshot", length = 1000)
    private String recommendationReasonSnapshot;

    @Column(name = "recommended_workout_day_id")
    private Long recommendedWorkoutDayId;

    @Column(name = "recommended_content", length = 500)
    private String recommendedContent;

    @Column(name = "pointer_previous_day_index")
    private Integer pointerPreviousDayIndex;

    @Column(name = "pointer_previous_last_workout_date")
    private LocalDateTime pointerPreviousLastWorkoutDate;

    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;

    @Column(name = "revoked_time")
    private LocalDateTime revokedTime;

    @Column(name = "revoked_reason", length = 500)
    private String revokedReason;

    @Column(name = "revoked_by", length = 50)
    private String revokedBy;

    /**
     * 训练日期，用于每日限制校验
     */
    @Column(name = "workout_date", nullable = false)
    private LocalDate workoutDate;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

}
