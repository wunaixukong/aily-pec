package com.ailypec.repository;

import com.ailypec.entity.TodayWorkoutChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TodayWorkoutChatMessageRepository extends JpaRepository<TodayWorkoutChatMessage, Long> {

    /**
     * 按推荐 ID 获取所有对话消息，按时间升序排列。
     */
    List<TodayWorkoutChatMessage> findByRecommendationIdOrderByCreateTimeAsc(Long recommendationId);

    /**
     * 删除关联的对话消息。
     */
    void deleteByRecommendationId(Long recommendationId);
}
