package com.ailypec.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "progress_pointers")
@Data
public class ProgressPointer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * 计划id
     */
    @Column(name = "active_plan_id", nullable = false)
    private Long activePlanId;

    @Column(name = "current_day_index")
    private Integer currentDayIndex = 0;

    @Column(name = "last_workout_date")
    private LocalDateTime lastWorkoutDate;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

}
