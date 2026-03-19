package com.ailypec.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "today_workout_recommendations")
@Data
public class TodayWorkoutRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "recommendation_date", nullable = false)
    private LocalDate recommendationDate;

    @Column(name = "base_workout_day_id", nullable = false)
    private Long baseWorkoutDayId;

    @Column(name = "base_content", nullable = false, length = 500)
    private String baseContent;

    @Column(name = "recommended_workout_day_id")
    private Long recommendedWorkoutDayId;

    @Column(name = "recommended_content", nullable = false, length = 500)
    private String recommendedContent;

    @Column(name = "recommendation_type", nullable = false, length = 50)
    private String recommendationType;

    @Column(name = "recommendation_reason", length = 1000)
    private String recommendationReason;

    @Column(name = "status_description_snapshot", length = 1000)
    private String statusDescriptionSnapshot;

    @Column(name = "fallback_used", nullable = false)
    private Boolean fallbackUsed = false;

    @Column(name = "completed", nullable = false)
    private Boolean completed = false;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
