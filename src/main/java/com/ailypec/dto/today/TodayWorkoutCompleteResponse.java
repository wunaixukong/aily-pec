package com.ailypec.dto.today;

import lombok.Data;

@Data
public class TodayWorkoutCompleteResponse {

    private Long recordId;
    private Long recommendationId;
    private String completionMode;
    private Boolean pointerAdvanced;
    private Integer nextDayIndex;
    private String nextWorkoutContent;
    private Long completedWorkoutDayId;
    private String completedContent;
}
