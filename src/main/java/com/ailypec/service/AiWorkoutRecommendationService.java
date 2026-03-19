package com.ailypec.service;

import com.ailypec.entity.TodayStatus;
import com.ailypec.entity.TodayWorkoutChatMessage;
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
     * 调用 AI 服务生成今日训练推荐，支持多轮对话上下文。
     */
    @Override
    public RecommendationResult recommend(TodayStatus todayStatus, List<TodayWorkoutChatMessage> chatHistory, WorkoutDay baseDay, List<WorkoutDay> orderedDays, int baseIndex) {
        if ((todayStatus == null || !StringUtils.hasText(todayStatus.getDescription())) && CollectionUtils.isEmpty(chatHistory)) {
            return fallbackToBase(baseDay, false, "今天按计划训练即可");
        }
        if (!enabled || !StringUtils.hasText(apiKey)) {
            return fallbackToBase(baseDay, true, "AI 服务未启用，已按计划返回今日训练");
        }

        List<WorkoutDay> candidates = buildCandidates(baseDay, orderedDays, baseIndex);
        try {
            Map<String, Object> payload = buildPayload(todayStatus, chatHistory, baseDay, candidates);
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
     * 构建允许 AI 选择的候选训练日列表 (现在包含整个计划的所有天)。
     */
    private List<WorkoutDay> buildCandidates(WorkoutDay baseDay, List<WorkoutDay> orderedDays, int baseIndex) {
        // 返回所有训练日，让 AI 有充分的选择空间（比如避开受伤部位）
        return new ArrayList<>(orderedDays);
    }

    /**
     * 组装调用 AI 接口所需的请求体 (Anthropic Messages API 格式，支持多轮对话)。
     */
    private Map<String, Object> buildPayload(TodayStatus todayStatus, List<TodayWorkoutChatMessage> chatHistory, WorkoutDay baseDay, List<WorkoutDay> candidates) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", 1024);

        String systemPrompt = "你是一位专业的健身教练。你的任务是根据用户的身体状态，从给定的候选训练日中推荐最合适的一个，或者给出恢复建议。\n" +
                "\n" +
                "核心逻辑：\n" +
                "1. 避让原则：如果用户提到某个身体部位受伤或疼痛（如'肩部拉伤'），你必须避开所有包含该部位训练的项目。\n" +
                "2. 智能替换：如果原计划（BASE_PLAN）涉及受伤部位，请从候选项目中挑选一个不涉及该部位的项目作为替代（ALTERNATIVE）。例如：肩部受伤时，如果候选中有'练腿'或'核心训练'，应优先选择它们。\n" +
                "3. 恢复建议：如果所有候选项目都会加重伤病，或者用户状态极差（如'发烧'、'极度疲劳'），请推荐 'RECOVERY' 并给出具体的恢复建议。\n" +
                "4. 优先级：原计划 > 合适的替代项目 > 恢复建议。\n" +
                "5. 对话理解：用户可能会对你之前的建议提出反馈（如“我不想做这个”，“换一个”），请结合对话历史灵活调整，并在每一轮回复中都返回最终确定的完整 JSON 方案。\n" +
                "\n" +
                "输出要求：\n" +
                "必须只输出 JSON 格式，严禁包含任何 Markdown 标签或解释文字。格式如下：\n" +
                "{\n" +
                "  \"recommendationType\": \"BASE_PLAN|ALTERNATIVE|RECOVERY\",\n" +
                "  \"recommendedWorkoutDayId\": 1,\n" +
                "  \"recommendedContent\": \"训练内容或恢复建议\",\n" +
                "  \"recommendationReason\": \"你的教练话术，包括对建议的解释，50字以内\"\n" +
                "}\n" +
                "注意：如果推荐类型是 RECOVERY，recommendedWorkoutDayId 填 null。";

        payload.put("system", systemPrompt);

        List<Map<String, String>> messages = new ArrayList<>();

        // 如果有历史记录，则使用历史记录
        if (!CollectionUtils.isEmpty(chatHistory)) {
            for (TodayWorkoutChatMessage msg : chatHistory) {
                messages.add(Map.of(
                        "role", msg.getRole(),
                        "content", msg.getContent()
                ));
            }
        } else {
            // 首轮推荐：使用 TodayStatus 发起
            messages.add(Map.of(
                    "role", "user",
                    "content", buildUserPrompt(todayStatus, baseDay, candidates)
            ));
        }

        payload.put("messages", messages);
        payload.put("temperature", 0.2);
        return payload;
    }

    /**
     * 构建发送给 AI 的首轮用户提示词。
     */
    private String buildUserPrompt(TodayStatus todayStatus, WorkoutDay baseDay, List<WorkoutDay> candidates) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户今日状态：").append(todayStatus.getDescription()).append("\n");
        builder.append("原计划训练：ID=").append(baseDay.getId()).append(", 内容=").append(baseDay.getContent()).append("\n");
        builder.append("候选训练项目：\n");
        for (WorkoutDay candidate : candidates) {
            builder.append("- ID=").append(candidate.getId()).append(", 内容=").append(candidate.getContent()).append("\n");
        }
        builder.append("只能从候选项目中选择一个最合适的推荐给用户，或者给出恢复建议。请记住避让原则。");
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
