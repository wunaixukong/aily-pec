package com.ailypec.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 番茄钟配置实体类
 * 用于保存用户的番茄钟时间配置
 */
@Entity
@Table(name = "pomodoro_configs")
@Data
public class PomodoroConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 工作时长（分钟）
     */
    @Column(name = "work_duration", nullable = false)
    private Integer workDuration = 25;

    /**
     * 短休息时长（分钟）
     */
    @Column(name = "short_break_duration", nullable = false)
    private Integer shortBreakDuration = 5;

    /**
     * 长休息时长（分钟）
     */
    @Column(name = "long_break_duration", nullable = false)
    private Integer longBreakDuration = 15;

    /**
     * 长休息间隔（完成几个工作周期后长休息）
     */
    @Column(name = "long_break_interval", nullable = false)
    private Integer longBreakInterval = 4;

    /**
     * 是否自动开始下一个周期
     */
    @Column(name = "auto_start")
    private Boolean autoStart = false;

    /**
     * 是否激活
     */
    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}