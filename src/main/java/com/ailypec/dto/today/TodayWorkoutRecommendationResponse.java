package com.ailypec.dto.today;

import lombok.Data;

@Data
public class TodayWorkoutRecommendationResponse {

    private Long recommendationId;
    private Long planId;
    private Long baseWorkoutDayId;
    private String baseContent;
    private Long recommendedWorkoutDayId;
    private String recommendedContent;
    private String recommendationType;
    private String recommendationReason;
    private String statusDescription;
    private Boolean fallbackUsed;
    private Boolean completed;
}
