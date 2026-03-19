package com.ailypec.repository;

import com.ailypec.entity.TodayWorkoutRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TodayWorkoutRecommendationRepository extends JpaRepository<TodayWorkoutRecommendation, Long> {

    Optional<TodayWorkoutRecommendation> findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(Long userId, LocalDate recommendationDate);

    Optional<TodayWorkoutRecommendation> findByIdAndUserId(Long id, Long userId);

    void deleteByUserIdAndRecommendationDateAndCompletedFalse(Long userId, LocalDate recommendationDate);

    java.util.List<TodayWorkoutRecommendation> findByUserIdAndRecommendationDateAndCompletedFalse(Long userId, LocalDate recommendationDate);
}
