package com.ailypec.service;

import com.ailypec.entity.TodayStatus;
import com.ailypec.dto.today.TodayWorkoutChatItem;
import com.ailypec.entity.WorkoutDay;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface WorkoutRecommendationService {

    /**
     * 根据今日状态、对话历史和候选训练日生成推荐结果。
     */
    RecommendationResult recommend(TodayStatus todayStatus, List<TodayWorkoutChatItem> chatHistory, WorkoutDay baseDay, List<WorkoutDay> orderedDays, int baseIndex);

    /**
     * 流式推荐：在 AI 回复过程中实时通过 callback 推送片段内容，最终返回完整响应字符串。
     */
    void recommendStream(TodayStatus todayStatus, List<TodayWorkoutChatItem> chatHistory, WorkoutDay baseDay, List<WorkoutDay> orderedDays, int baseIndex, Consumer<String> onToken, Consumer<String> onComplete);

    /**
     * 解析 AI 文本内容。
     */
    RecommendationResult parseResult(String content, WorkoutDay baseDay, List<WorkoutDay> orderedDays);

    /**
     * 从完整模型输出中尽量提取前端可见的 recommendationReason 文本。
     */
    String extractVisibleRecommendationReason(String content);

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
