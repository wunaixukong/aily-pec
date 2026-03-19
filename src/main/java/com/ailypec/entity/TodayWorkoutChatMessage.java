package com.ailypec.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 今日训练对话消息实体类
 */
@Entity
@Table(name = "today_workout_chat_messages")
@Data
public class TodayWorkoutChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    /**
     * 角色: user (用户), assistant (AI)
     */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    /**
     * 消息内容
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;
}
