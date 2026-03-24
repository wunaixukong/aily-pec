package com.ailypec.dto.today;

import lombok.Data;

@Data
public class TodayWorkoutNextResponse {
    private Long workoutDayId;
    private Integer dayOrder;
    private String content;
    private Long planId;
}
