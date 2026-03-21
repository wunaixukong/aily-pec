package com.ailypec.service;

import com.ailypec.entity.TodayWorkoutChatSession;
import com.ailypec.repository.TodayWorkoutChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TodayWorkoutChatSessionFlushScheduler {

    private static final String SESSION_KEY_PREFIX = "today_workout_chat:";
    private static final String DIRTY_SET_KEY = "today_workout_chat:dirty";

    private final StringRedisTemplate stringRedisTemplate;
    private final TodayWorkoutChatSessionRepository todayWorkoutChatSessionRepository;

    @Scheduled(fixedDelayString = "${app.today-workout-chat.flush-interval-ms:30000}")
    @Transactional
    public void flushDirtySessions() {
        Set<String> dirtyIds = stringRedisTemplate.opsForSet().members(DIRTY_SET_KEY);
        if (CollectionUtils.isEmpty(dirtyIds)) {
            return;
        }

        for (String dirtyId : new LinkedHashSet<>(dirtyIds)) {
            Long recommendationId = parseRecommendationId(dirtyId);
            if (recommendationId == null) {
                stringRedisTemplate.opsForSet().remove(DIRTY_SET_KEY, dirtyId);
                continue;
            }

            String json = stringRedisTemplate.opsForValue().get(redisKey(recommendationId));
            if (!StringUtils.hasText(json)) {
                log.warn("Skip flushing chat session because Redis payload is missing, recommendationId={}", recommendationId);
                stringRedisTemplate.opsForSet().remove(DIRTY_SET_KEY, dirtyId);
                continue;
            }

            try {
                todayWorkoutChatSessionRepository.findByRecommendationId(recommendationId)
                        .map(existing -> updateSession(existing, json))
                        .orElseGet(() -> createSession(recommendationId, json));
                stringRedisTemplate.opsForSet().remove(DIRTY_SET_KEY, dirtyId);
            } catch (RuntimeException e) {
                log.warn("Failed to flush chat session, recommendationId={}, error={}", recommendationId, e.getMessage());
            }
        }
    }

    private TodayWorkoutChatSession updateSession(TodayWorkoutChatSession session, String json) {
        session.setContent(json);
        return todayWorkoutChatSessionRepository.save(session);
    }

    private TodayWorkoutChatSession createSession(Long recommendationId, String json) {
        TodayWorkoutChatSession session = new TodayWorkoutChatSession();
        session.setRecommendationId(recommendationId);
        session.setContent(json);
        return todayWorkoutChatSessionRepository.save(session);
    }

    private Long parseRecommendationId(String dirtyId) {
        try {
            return Long.parseLong(dirtyId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String redisKey(Long recommendationId) {
        return SESSION_KEY_PREFIX + recommendationId;
    }
}
