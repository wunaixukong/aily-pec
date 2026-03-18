package com.ailypec.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 番茄钟配置实体类
 * 用于保存用户的番茄钟时间配置
 * 支持多条配置绑定不同生效时间段
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
     * 配置名称（如"上午工作"、"下午工作"）
     */
    @Column(name = "config_name", length = 50)
    private String configName = "默认配置";

    /**
     * 工作时长（分钟）
     */
    @Column(name = "work_duration", nullable = false)
    private Integer workDuration = 25;

    /**
     * 休息时长（分钟）
     */
    @Column(name = "break_duration", nullable = false)
    private Integer breakDuration = 5;

    /**
     * 生效开始时间
     */
    @JsonFormat(pattern = "HH:mm")
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime = LocalTime.of(9, 0);

    /**
     * 生效结束时间
     */
    @JsonFormat(pattern = "HH:mm")
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime = LocalTime.of(18, 0);

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
