package com.ailypec.service;

import com.ailypec.entity.TodayStatus;
import com.ailypec.entity.WorkoutDay;

import java.util.List;
import java.util.Optional;

public interface WorkoutRecommendationService {

    /**
     * 根据今日状态和候选训练日生成推荐结果。
     */
    RecommendationResult recommend(TodayStatus todayStatus, WorkoutDay baseDay, List<WorkoutDay> orderedDays, int baseIndex);

    record RecommendationResult(
            Long recommendedWorkoutDayId,
            String recommendedContent,
            String recommendationType,
            String recommendationReason,
            boolean fallbackUsed
    ) {
    }

    /**
     * 根据训练日 ID 在候选列表中查找对应训练日。
     */
    Optional<WorkoutDay> findRecommendedDayById(List<WorkoutDay> orderedDays, Long workoutDayId);
}
