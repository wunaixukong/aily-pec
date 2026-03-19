package com.ailypec.service;

import com.ailypec.entity.TodayStatus;
import com.ailypec.entity.WorkoutDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkoutRecommendationService implements WorkoutRecommendationService {

    private static final String TYPE_BASE_PLAN = "BASE_PLAN";
    private static final String TYPE_ALTERNATIVE = "ALTERNATIVE";
    private static final String TYPE_RECOVERY = "RECOVERY";

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${app.ai.workout.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${app.ai.workout.chat-path:/chat/completions}")
    private String chatPath;

    @Value("${app.ai.workout.model:deepseek-chat}")
    private String model;

    @Value("${app.ai.workout.enabled:true}")
    private boolean enabled;

    /**
     * 调用 AI 服务生成今日训练推荐，并在失败时回退到原计划训练。
     */
    @Override
    public RecommendationResult recommend(TodayStatus todayStatus, WorkoutDay baseDay, List<WorkoutDay> orderedDays, int baseIndex) {
        if (todayStatus == null || !StringUtils.hasText(todayStatus.getDescription())) {
            return fallbackToBase(baseDay, false, "今天按计划训练即可");
        }
        if (!enabled || !StringUtils.hasText(apiKey)) {
            return fallbackToBase(baseDay, true, "AI 服务未启用，已按计划返回今日训练");
        }

        List<WorkoutDay> candidates = buildCandidates(baseDay, orderedDays, baseIndex);
        try {
            Map<String, Object> payload = buildPayload(todayStatus, baseDay, candidates);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofSeconds(5))
                    .setReadTimeout(Duration.ofSeconds(60))
                    .build();

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + chatPath,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );

            RecommendationResult result = parseResponse(response.getBody(), baseDay, orderedDays);
            if (result == null) {
                return fallbackToBase(baseDay, true, "AI 推荐结果无效，已按计划返回今日训练");
            }
            return result;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("AI recommendation failed: {}", e.getMessage());
            return fallbackToBase(baseDay, true, "AI 推荐暂时不可用，已按计划返回今日训练");
        }
    }

    /**
     * 在候选训练日列表中查找推荐训练日。
     */
    @Override
    public Optional<WorkoutDay> findRecommendedDayById(List<WorkoutDay> orderedDays, Long workoutDayId) {
        if (workoutDayId == null) {
            return Optional.empty();
        }
        return orderedDays.stream().filter(day -> workoutDayId.equals(day.getId())).findFirst();
    }

    /**
     * 构建允许 AI 选择的候选训练日列表。
     */
    private List<WorkoutDay> buildCandidates(WorkoutDay baseDay, List<WorkoutDay> orderedDays, int baseIndex) {
        List<WorkoutDay> candidates = new ArrayList<>();
        candidates.add(baseDay);
        if (baseIndex > 0) {
            candidates.add(orderedDays.get(baseIndex - 1));
        }
        if (baseIndex < orderedDays.size() - 1) {
            candidates.add(orderedDays.get(baseIndex + 1));
        }
        return candidates.stream().distinct().toList();
    }

    /**
     * 组装调用 AI 接口所需的请求体 (Anthropic Messages API 格式)。
     */
    private Map<String, Object> buildPayload(TodayStatus todayStatus, WorkoutDay baseDay, List<WorkoutDay> candidates) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", 1024);
        payload.put("system", "你是训练推荐助手。你只能从候选项目中选择一个训练项目，或者给出 RECOVERY 恢复建议。请只输出 JSON，格式为 {\"recommendationType\":\"BASE_PLAN|ALTERNATIVE|RECOVERY\",\"recommendedWorkoutDayId\":1,\"recommendedContent\":\"xxx\",\"recommendationReason\":\"xxx\"}。如果 recommendationType 是 RECOVERY，则 recommendedWorkoutDayId 可为 null，recommendedContent 填恢复建议。不要包含任何 Markdown 格式。");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "user",
                "content", buildUserPrompt(todayStatus, baseDay, candidates)
        ));
        payload.put("messages", messages);
        payload.put("temperature", 0.2);
        return payload;
    }

    /**
     * 构建发送给 AI 的用户提示词。
     */
    private String buildUserPrompt(TodayStatus todayStatus, WorkoutDay baseDay, List<WorkoutDay> candidates) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户今日状态：").append(todayStatus.getDescription()).append("\n");
        builder.append("原计划训练：ID=").append(baseDay.getId()).append(", 内容=").append(baseDay.getContent()).append("\n");
        builder.append("候选训练项目：\n");
        for (WorkoutDay candidate : candidates) {
            builder.append("- ID=").append(candidate.getId()).append(", 内容=").append(candidate.getContent()).append("\n");
        }
        builder.append("只能返回原计划、相邻训练日之一，或恢复建议。不要返回候选之外的训练项目。原因控制在50字内。");
        return builder.toString();
    }

    /**
     * 解析 AI 返回内容并转换为受控推荐结果 (Anthropic Messages API 格式)。
     */
    @SuppressWarnings("unchecked")
    private RecommendationResult parseResponse(Map<String, Object> body, WorkoutDay baseDay, List<WorkoutDay> orderedDays) {
        if (body == null) {
            return null;
        }
        Object contentObject = body.get("content");
        if (!(contentObject instanceof List<?> contentList) || CollectionUtils.isEmpty(contentList)) {
            return null;
        }
        Object firstItem = contentList.get(0);
        if (!(firstItem instanceof Map<?, ?> contentMap)) {
            return null;
        }
        Object textObject = contentMap.get("text");
        if (!(textObject instanceof String content) || !StringUtils.hasText(content)) {
            return null;
        }

        String recommendationType = extractJsonString(content, "recommendationType");
        String recommendedContent = extractJsonString(content, "recommendedContent");
        String recommendationReason = extractJsonString(content, "recommendationReason");
        Long recommendedWorkoutDayId = extractJsonLong(content, "recommendedWorkoutDayId");

        if (!StringUtils.hasText(recommendationType) || !StringUtils.hasText(recommendedContent)) {
            return null;
        }
        if (TYPE_RECOVERY.equals(recommendationType)) {
            return new RecommendationResult(null, recommendedContent, TYPE_RECOVERY, defaultReason(recommendationReason), false);
        }
        Optional<WorkoutDay> recommendedDay = findRecommendedDayById(orderedDays, recommendedWorkoutDayId);
        if (recommendedDay.isEmpty()) {
            return null;
        }
        WorkoutDay day = recommendedDay.get();
        String finalType = baseDay.getId().equals(day.getId()) ? TYPE_BASE_PLAN : TYPE_ALTERNATIVE;
        return new RecommendationResult(day.getId(), day.getContent(), finalType, defaultReason(recommendationReason), false);
    }

    /**
     * 从 JSON 字符串中提取指定字段的字符串值。
     */
    private String extractJsonString(String json, String field) {
        String pattern = "\"" + field + "\"";
        int fieldIndex = json.indexOf(pattern);
        if (fieldIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', fieldIndex + pattern.length());
        if (colonIndex < 0) {
            return null;
        }
        int firstQuote = json.indexOf('"', colonIndex + 1);
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        return json.substring(firstQuote + 1, secondQuote).trim();
    }

    /**
     * 从 JSON 字符串中提取指定字段的 Long 值。
     */
    private Long extractJsonLong(String json, String field) {
        String pattern = "\"" + field + "\"";
        int fieldIndex = json.indexOf(pattern);
        if (fieldIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', fieldIndex + pattern.length());
        if (colonIndex < 0) {
            return null;
        }
        String remain = json.substring(colonIndex + 1).trim();
        if (remain.startsWith("null")) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (char c : remain.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        return Long.valueOf(digits.toString());
    }

    /**
     * 回退到原计划训练内容。
     */
    private RecommendationResult fallbackToBase(WorkoutDay baseDay, boolean fallbackUsed, String reason) {
        return new RecommendationResult(
                baseDay.getId(),
                baseDay.getContent(),
                TYPE_BASE_PLAN,
                reason,
                fallbackUsed
        );
    }

    /**
     * 为推荐原因提供默认文案。
     */
    private String defaultReason(String reason) {
        return StringUtils.hasText(reason) ? reason : "按当前状态生成的推荐";
    }
}
