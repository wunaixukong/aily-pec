package com.ailypec.dto.today;

import lombok.Data;

@Data
public class TodayWorkoutCompleteRequest {

    private Long recommendationId;
    private String completionMode;
    private Long completedWorkoutDayId;
    private String completedContent;
}
