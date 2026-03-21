package com.ailypec.service;

import com.ailypec.dto.today.TodayWorkoutChatHistoryResponse;
import com.ailypec.dto.today.TodayWorkoutChatItem;
import com.ailypec.dto.today.TodayWorkoutRenderBlock;
import com.ailypec.entity.TodayWorkoutChatSession;
import com.ailypec.repository.TodayWorkoutChatSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TodayWorkoutChatSessionService {

    private static final String SESSION_KEY_PREFIX = "today_workout_chat:";
    private static final String DIRTY_SET_KEY = "today_workout_chat:dirty";
    private static final Duration SESSION_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;
    private final TodayWorkoutChatSessionRepository todayWorkoutChatSessionRepository;
    private final ObjectMapper objectMapper;

    public void appendMessage(Long recommendationId, String role, String content) {
        if (recommendationId == null || !StringUtils.hasText(role) || !StringUtils.hasText(content)) {
            return;
        }

        ChatSessionContent sessionContent = loadSessionContent(recommendationId);
        sessionContent.getMessages().add(new TodayWorkoutChatItem(role, content.trim(), LocalDateTime.now()));
        saveToRedis(recommendationId, sessionContent, true);
    }

    public void setPendingBlocks(Long recommendationId, List<TodayWorkoutRenderBlock> pendingBlocks) {
        if (recommendationId == null) {
            return;
        }
        ChatSessionContent sessionContent = loadSessionContent(recommendationId);
        sessionContent.setPendingBlocks(pendingBlocks);
        saveToRedis(recommendationId, sessionContent, true);
    }

    public void clearPendingBlocks(Long recommendationId) {
        setPendingBlocks(recommendationId, null);
    }

    public List<TodayWorkoutChatItem> getMessages(Long recommendationId) {
        if (recommendationId == null) {
            return List.of();
        }
        return new ArrayList<>(loadSessionContent(recommendationId).getMessages());
    }

    public TodayWorkoutChatHistoryResponse getHistory(Long recommendationId) {
        TodayWorkoutChatHistoryResponse response = new TodayWorkoutChatHistoryResponse();
        response.setRecommendationId(recommendationId);
        ChatSessionContent sessionContent = loadSessionContent(recommendationId);
        response.setMessages(new ArrayList<>(sessionContent.getMessages()));
        response.setPendingBlocks(new ArrayList<>(sessionContent.getPendingBlocks()));
        return response;
    }

    public void deleteSession(Long recommendationId) {
        if (recommendationId == null) {
            return;
        }
        stringRedisTemplate.delete(redisKey(recommendationId));
        stringRedisTemplate.opsForSet().remove(DIRTY_SET_KEY, recommendationId.toString());
        todayWorkoutChatSessionRepository.deleteByRecommendationId(recommendationId);
    }

    private ChatSessionContent loadSessionContent(Long recommendationId) {
        String redisJson = stringRedisTemplate.opsForValue().get(redisKey(recommendationId));
        if (StringUtils.hasText(redisJson)) {
            return deserialize(redisJson);
        }

        return todayWorkoutChatSessionRepository.findByRecommendationId(recommendationId)
                .map(TodayWorkoutChatSession::getContent)
                .filter(StringUtils::hasText)
                .map(content -> {
                    ChatSessionContent loaded = deserialize(content);
                    saveToRedis(recommendationId, loaded, false);
                    return loaded;
                })
                .orElseGet(ChatSessionContent::new);
    }

    private void saveToRedis(Long recommendationId, ChatSessionContent sessionContent, boolean dirty) {
        try {
            String json = objectMapper.writeValueAsString(sessionContent);
            String redisKey = redisKey(recommendationId);
            stringRedisTemplate.opsForValue().set(redisKey, json, SESSION_TTL);
            if (dirty) {
                stringRedisTemplate.opsForSet().add(DIRTY_SET_KEY, recommendationId.toString());
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chat session", e);
        }
    }

    private ChatSessionContent deserialize(String json) {
        try {
            ChatSessionContent content = objectMapper.readValue(json, ChatSessionContent.class);
            if (content.getMessages() == null) {
                content.setMessages(new ArrayList<>());
            }
            if (content.getVersion() == null) {
                content.setVersion(1);
            }
            return content;
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize chat session JSON, returning empty content: {}", e.getMessage());
            return new ChatSessionContent();
        }
    }

    private String redisKey(Long recommendationId) {
        return SESSION_KEY_PREFIX + recommendationId;
    }

    @lombok.Data
    public static class ChatSessionContent {
        private Integer version = 1;
        private List<TodayWorkoutChatItem> messages = new ArrayList<>();
        private List<TodayWorkoutRenderBlock> pendingBlocks = new ArrayList<>();

        public List<TodayWorkoutChatItem> getMessages() {
            return messages == null ? Collections.emptyList() : messages;
        }

        public void setMessages(List<TodayWorkoutChatItem> messages) {
            this.messages = messages == null ? new ArrayList<>() : messages;
        }

        public List<TodayWorkoutRenderBlock> getPendingBlocks() {
            return pendingBlocks == null ? Collections.emptyList() : pendingBlocks;
        }

        public void setPendingBlocks(List<TodayWorkoutRenderBlock> pendingBlocks) {
            this.pendingBlocks = pendingBlocks == null ? new ArrayList<>() : pendingBlocks;
        }
    }
}
