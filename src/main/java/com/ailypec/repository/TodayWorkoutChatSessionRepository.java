package com.ailypec.repository;

import com.ailypec.entity.TodayWorkoutChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TodayWorkoutChatSessionRepository extends JpaRepository<TodayWorkoutChatSession, Long> {

    Optional<TodayWorkoutChatSession> findByRecommendationId(Long recommendationId);

    void deleteByRecommendationId(Long recommendationId);
}
