package com.ailypec.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ailypec.config.AiWorkoutProperties;
import com.ailypec.dto.today.TodayWorkoutChatItem;
import com.ailypec.entity.TodayStatus;
import com.ailypec.entity.WorkoutDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkoutRecommendationService implements WorkoutRecommendationService {

    private static final String TYPE_BASE_PLAN = "BASE_PLAN";
    private static final String TYPE_ALTERNATIVE = "ALTERNATIVE";
    private static final String TYPE_RECOVERY = "RECOVERY";
    private static final String ACTION_UNDO_COMPLETE = "UNDO_COMPLETE";
    private static final String RECOMMENDATION_REASON_FIELD = "\"recommendationReason\"";

    private final RestTemplateBuilder restTemplateBuilder;
    private final AiWorkoutProperties aiWorkoutProperties;

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(60))
            .build();

    private final ConcurrentMap<String, Instant> routeCooldownUntil = new ConcurrentHashMap<>();

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    private volatile String preferredRouteName;

    @Override
    public void recommendStream(TodayStatus todayStatus,
                                List<TodayWorkoutChatItem> chatHistory,
                                WorkoutDay baseDay,
                                List<WorkoutDay> orderedDays,
                                int baseIndex,
                                boolean completedContext,
                                Consumer<String> onToken,
                                Consumer<String> onComplete) {
        // 先解析当前可用路由，并按“非冷却中 > 上次成功 > 名称稳定排序”排好顺序。
        List<ResolvedRoute> routes = resolveRoutes();
        if (!aiWorkoutProperties.isEnabled() || routes.isEmpty()) {
            log.warn("AI stream skipped because service is disabled or no route is available");
            onComplete.accept(null);
            return;
        }

        log.info("AI stream routing prepared, routeOrder={}", summarizeRoutes(routes));
        List<WorkoutDay> candidates = buildCandidates(baseDay, orderedDays, baseIndex);
        attemptStream(routes, 0, todayStatus, chatHistory, baseDay, candidates, orderedDays, completedContext, onToken, onComplete);
    }

    @Override
    public RecommendationResult recommend(TodayStatus todayStatus,
                                          List<TodayWorkoutChatItem> chatHistory,
                                          WorkoutDay baseDay,
                                          List<WorkoutDay> orderedDays,
                                          int baseIndex,
                                          boolean completedContext) {
        if ((todayStatus == null || !StringUtils.hasText(todayStatus.getDescription())) && CollectionUtils.isEmpty(chatHistory) && !completedContext) {
            return fallbackToBase(baseDay, false, "今天按计划训练即可");
        }

        List<ResolvedRoute> routes = resolveRoutes();
        if (!aiWorkoutProperties.isEnabled() || routes.isEmpty()) {
            log.warn("AI recommend skipped because service is disabled or no route is available");
            return fallbackToBase(baseDay, true, "AI 服务未启用，已按计划返回今日训练");
        }

        log.info("AI recommend routing prepared, routeOrder={}", summarizeRoutes(routes));
        List<WorkoutDay> candidates = buildCandidates(baseDay, orderedDays, baseIndex);
        // 同步请求采用顺序重试：当前路由失败就立即切到下一条，直到成功或全部失败。
        for (ResolvedRoute route : routes) {
            try {
                log.info("Trying AI route {}, apiType={}, url={}, model={}",
                        route.name(), route.apiType(), route.requestUrl(), route.model());
                Map<String, Object> payload = buildPayload(route, todayStatus, chatHistory, baseDay, candidates, completedContext);
                HttpHeaders headers = buildHeaders(route);

                RestTemplate restTemplate = restTemplateBuilder
                        .setConnectTimeout(Duration.ofSeconds(5))
                        .setReadTimeout(Duration.ofSeconds(60))
                        .build();

                ResponseEntity<Map> response = restTemplate.exchange(
                        route.requestUrl(),
                        HttpMethod.POST,
                        new HttpEntity<>(payload, headers),
                        Map.class
                );

                RecommendationResult result = parseResponse(route, response.getBody(), baseDay, orderedDays);
                if (result != null) {
                    markRouteSuccess(route);
                    log.info("AI route {} succeeded for recommend request", route.name());
                    return result;
                }

                markRouteFailure(route, "invalid-response");
                log.warn("AI route {} returned an invalid response body", route.name());
            } catch (RestClientException | IllegalArgumentException e) {
                markRouteFailure(route, e.getMessage());
                log.warn("AI route {} failed: {}", route.name(), e.getMessage());
            }
        }

        log.warn("All AI routes failed for recommend request, fallback to base plan");
        return fallbackToBase(baseDay, true, "AI 路由暂时不可用，已按计划返回今日训练");
    }

    @Override
    public Optional<WorkoutDay> findRecommendedDayById(List<WorkoutDay> orderedDays, Long workoutDayId) {
        if (workoutDayId == null) {
            return Optional.empty();
        }
        return orderedDays.stream().filter(day -> workoutDayId.equals(day.getId())).findFirst();
    }

    @Override
    public RecommendationResult parseResult(String content, WorkoutDay baseDay, List<WorkoutDay> orderedDays) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        RecommendationPayload payload = parseRecommendationPayload(content);
        if (payload == null) {
            return null;
        }

        if (!StringUtils.hasText(payload.recommendationType()) || !StringUtils.hasText(payload.recommendedContent())) {
            return null;
        }
        if (TYPE_RECOVERY.equals(payload.recommendationType())) {
            return new RecommendationResult(null, payload.recommendedContent(), TYPE_RECOVERY, defaultReason(payload.recommendationReason()), false, payload.actionProposal());
        }
        Optional<WorkoutDay> recommendedDay = findRecommendedDayById(orderedDays, payload.recommendedWorkoutDayId());
        if (recommendedDay.isEmpty()) {
            return null;
        }
        WorkoutDay day = recommendedDay.get();
        String finalType = baseDay.getId().equals(day.getId()) ? TYPE_BASE_PLAN : TYPE_ALTERNATIVE;
        return new RecommendationResult(day.getId(), day.getContent(), finalType, defaultReason(payload.recommendationReason()), false, payload.actionProposal());
    }

    private void attemptStream(List<ResolvedRoute> routes,
                               int routeIndex,
                               TodayStatus todayStatus,
                               List<TodayWorkoutChatItem> chatHistory,
                               WorkoutDay baseDay,
                               List<WorkoutDay> candidates,
                               List<WorkoutDay> orderedDays,
                               boolean completedContext,
                               Consumer<String> onToken,
                               Consumer<String> onComplete) {
        // 流式请求也按顺序切路由，但只有在“还没给前端吐 token”时才安全重试。
        // 一旦已经输出部分内容，就不能无缝换源，否则前端会收到拼接后的脏数据。
        if (routeIndex >= routes.size()) {
            log.warn("All AI stream routes failed, no more route to retry");
            onComplete.accept(null);
            return;
        }

        ResolvedRoute route = routes.get(routeIndex);
        AtomicBoolean tokenDelivered = new AtomicBoolean(false);

        try {
            log.info("Trying AI stream route {}, index={}, apiType={}, url={}, model={}",
                    route.name(), routeIndex, route.apiType(), route.requestUrl(), route.model());
            Map<String, Object> payload = buildPayload(route, todayStatus, chatHistory, baseDay, candidates, completedContext);
            payload.put("stream", true);

            String jsonPayload = JSON.toJSONString(payload);
            Request request = buildStreamRequest(route, jsonPayload);

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    markRouteFailure(route, e.getMessage());
                    log.warn("AI stream route {} failed: {}", route.name(), e.getMessage());
                    if (tokenDelivered.get()) {
                        onComplete.accept(null);
                        return;
                    }
                    attemptStream(routes, routeIndex + 1, todayStatus, chatHistory, baseDay, candidates, orderedDays, completedContext, onToken, onComplete);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        markRouteFailure(route, "http-" + response.code());
                        log.warn("AI stream route {} returned status {}", route.name(), response.code());
                        if (tokenDelivered.get()) {
                            log.warn("AI stream route {} failed after tokens had been delivered, stop retrying", route.name());
                            onComplete.accept(null);
                            return;
                        }
                        log.info("Retrying AI stream with next route after {} failed", route.name());
                        attemptStream(routes, routeIndex + 1, todayStatus, chatHistory, baseDay, candidates, orderedDays, completedContext, onToken, onComplete);
                        return;
                    }

                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody == null) {
                            markRouteFailure(route, "empty-body");
                            log.warn("AI stream route {} returned empty body, switching to next route", route.name());
                            attemptStream(routes, routeIndex + 1, todayStatus, chatHistory, baseDay, candidates, orderedDays, completedContext, onToken, onComplete);
                            return;
                        }

                        String fullContent = readStreamContent(responseBody, onToken, tokenDelivered);
                        if (!StringUtils.hasText(fullContent)) {
                            markRouteFailure(route, "empty-content");
                            if (tokenDelivered.get()) {
                                log.warn("AI stream route {} ended with empty content after partial delivery", route.name());
                                onComplete.accept(null);
                            } else {
                                log.info("AI stream route {} produced empty content, retrying next route", route.name());
                                attemptStream(routes, routeIndex + 1, todayStatus, chatHistory, baseDay, candidates, orderedDays, completedContext, onToken, onComplete);
                            }
                            return;
                        }

                        markRouteSuccess(route);
                        log.info("AI stream route {} succeeded, contentLength={}, tokenDelivered={}",
                                route.name(), fullContent.length(), tokenDelivered.get());
                        onComplete.accept(fullContent);
                    } catch (Exception e) {
                        markRouteFailure(route, e.getMessage());
                        log.warn("AI stream route {} parse failed: {}", route.name(), e.getMessage());
                        if (tokenDelivered.get()) {
                            log.warn("AI stream route {} parse failed after partial delivery, stop retrying", route.name());
                            onComplete.accept(null);
                            return;
                        }
                        log.info("Retrying AI stream with next route after {} parse failure", route.name());
                        attemptStream(routes, routeIndex + 1, todayStatus, chatHistory, baseDay, candidates, orderedDays, completedContext, onToken, onComplete);
                    }
                }
            });
        } catch (Exception e) {
            markRouteFailure(route, e.getMessage());
            log.warn("AI stream route {} request build failed: {}", route.name(), e.getMessage());
            log.info("Retrying AI stream with next route after {} request build failure", route.name());
            attemptStream(routes, routeIndex + 1, todayStatus, chatHistory, baseDay, candidates, orderedDays, completedContext, onToken, onComplete);
        }
    }

    private String readStreamContent(ResponseBody responseBody,
                                     Consumer<String> onToken,
                                     AtomicBoolean tokenDelivered) throws IOException {
        InputStream inputStream = responseBody.byteStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        StringBuilder fullContent = new StringBuilder();
        RecommendationReasonStreamParser reasonParser = new RecommendationReasonStreamParser();

        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data: ")) {
                continue;
            }

            String data = line.substring(6).trim();
            if ("[DONE]".equals(data)) {
                break;
            }

            try {
                JSONObject chunk = JSON.parseObject(data);
                String token = extractStreamToken(chunk);
                if (!StringUtils.hasText(token)) {
                    continue;
                }

                fullContent.append(token);
                if (reasonParser.pushToken(token, onToken)) {
                    tokenDelivered.set(true);
                }
            } catch (Exception ignored) {
                // Ignore malformed stream chunks and continue consuming the stream.
            }
        }

        return fullContent.toString();
    }

    private String extractStreamToken(JSONObject body) {
        if (body == null || body.isEmpty()) {
            return null;
        }

        JSONArray choices = body.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject firstChoice = choices.getJSONObject(0);
            if (firstChoice != null) {
                JSONObject delta = firstChoice.getJSONObject("delta");
                if (delta != null) {
                    String content = delta.getString("content");
                    if (StringUtils.hasText(content)) {
                        return content;
                    }
                }
            }
        }

        if ("content_block_delta".equals(body.get("type"))) {
            JSONObject delta = body.getJSONObject("delta");
            if (delta != null && "text_delta".equals(delta.getString("type"))) {
                String text = delta.getString("text");
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }

        return null;
    }

    private List<WorkoutDay> buildCandidates(WorkoutDay baseDay, List<WorkoutDay> orderedDays, int baseIndex) {
        return new ArrayList<>(orderedDays);
    }

    private Map<String, Object> buildPayload(ResolvedRoute route,
                                             TodayStatus todayStatus,
                                             List<TodayWorkoutChatItem> chatHistory,
                                             WorkoutDay baseDay,
                                             List<WorkoutDay> candidates,
                                             boolean completedContext) {
        String systemPrompt = buildSystemPrompt(completedContext);
        List<Map<String, String>> conversation = buildConversation(todayStatus, chatHistory, baseDay, candidates, completedContext);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", route.model());
        payload.put("temperature", 0.2);

        if (route.apiType() == ApiType.ANTHROPIC) {
            payload.put("max_tokens", 1024);
            payload.put("system", systemPrompt);
            payload.put("messages", conversation);
            return payload;
        }

        payload.put("max_tokens", 1024);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(conversation);
        payload.put("messages", messages);
        return payload;
    }

    private List<Map<String, String>> buildConversation(TodayStatus todayStatus,
                                                        List<TodayWorkoutChatItem> chatHistory,
                                                        WorkoutDay baseDay,
                                                        List<WorkoutDay> candidates,
                                                        boolean completedContext) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (!CollectionUtils.isEmpty(chatHistory)) {
            for (TodayWorkoutChatItem msg : chatHistory) {
                messages.add(Map.of(
                        "role", msg.getRole(),
                        "content", msg.getContent()
                ));
            }
            return messages;
        }

        messages.add(Map.of(
                "role", "user",
                "content", buildUserPrompt(todayStatus, baseDay, candidates, completedContext)
        ));
        return messages;
    }

    private HttpHeaders buildHeaders(ResolvedRoute route) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (route.apiType() == ApiType.ANTHROPIC) {
            headers.set("x-api-key", route.apiKey());
            headers.set("anthropic-version", "2023-06-01");
            return headers;
        }

        headers.setBearerAuth(route.apiKey());
        return headers;
    }

    private Request buildStreamRequest(ResolvedRoute route, String jsonPayload) {
        Request.Builder builder = new Request.Builder()
                .url(route.requestUrl())
                .post(RequestBody.create(jsonPayload, okhttp3.MediaType.get("application/json")));

        if (route.apiType() == ApiType.ANTHROPIC) {
            builder.header("x-api-key", route.apiKey());
            builder.header("anthropic-version", "2023-06-01");
        } else {
            builder.header("Authorization", "Bearer " + route.apiKey());
        }

        return builder.build();
    }

    private RecommendationResult parseResponse(ResolvedRoute route,
                                               Map<String, Object> body,
                                               WorkoutDay baseDay,
                                               List<WorkoutDay> orderedDays) {
        if (body == null) {
            return null;
        }

        String content = route.apiType() == ApiType.ANTHROPIC
                ? extractAnthropicContent(body)
                : extractOpenAiContent(body);
        return parseResult(content, baseDay, orderedDays);
    }

    @SuppressWarnings("unchecked")
    private String extractAnthropicContent(Map<String, Object> body) {
        Object contentObject = body.get("content");
        if (!(contentObject instanceof List<?> contentList) || CollectionUtils.isEmpty(contentList)) {
            return null;
        }
        Object firstItem = contentList.get(0);
        if (!(firstItem instanceof Map<?, ?> contentMap)) {
            return null;
        }
        Object textObject = contentMap.get("text");
        return textObject instanceof String text && StringUtils.hasText(text) ? text : null;
    }

    @SuppressWarnings("unchecked")
    private String extractOpenAiContent(Map<String, Object> body) {
        Object choicesObject = body.get("choices");
        if (!(choicesObject instanceof List<?> choices) || CollectionUtils.isEmpty(choices)) {
            return null;
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return null;
        }
        Object messageObject = choiceMap.get("message");
        if (!(messageObject instanceof Map<?, ?> messageMap)) {
            return null;
        }
        Object contentObject = messageMap.get("content");
        return contentObject instanceof String content && StringUtils.hasText(content) ? content : null;
    }

    private String buildSystemPrompt(boolean completedContext) {
        if (completedContext) {
            return """
                    你是一位专业的健身教练，同时负责处理用户对今日已打卡训练的纠错咨询。

                    当前场景：今日训练已经打卡完成。你不能改动数据库，也不能直接执行撤回，只能决定是否提议一个待确认动作。

                    输出要求：
                    必须只输出 JSON，禁止包含 Markdown 或额外解释。格式如下：
                    {
                      "recommendationType": "BASE_PLAN",
                      "recommendedWorkoutDayId": 1,
                      "recommendedContent": "原训练内容",
                      "recommendationReason": "给用户的简短回复，不超过 50 字",
                      "action": {
                        "actionType": "UNDO_COMPLETE",
                        "title": "撤回今天的打卡",
                        "impact": "撤回后今日训练会恢复为未完成，并回滚本次推进的训练进度。",
                        "confirmText": "确认撤回"
                      }
                    }

                    规则：
                    1. 只有当用户明确表达“误触打卡、想撤回、帮我取消今天打卡”等撤回意图时，才返回 action。
                    2. 如果不是撤回诉求，action 必须为 null，并明确提示“今日已打卡，如为误触可让我帮你撤回”。
                    3. recommendationType、recommendedWorkoutDayId、recommendedContent 仅用于兼容现有结构，沿用当前方案即可，不要虚构新的训练推荐。
                    """;
        }
        return """
                你是一位专业的健身教练。你的任务是根据用户的身体状态，从给定的候选训练日中推荐最合适的一个，或者给出恢复建议。

                核心逻辑：
                1. 避让原则：如果用户提到某个身体部位受伤或疼痛，你必须避开所有包含该部位训练的项目。
                2. 智能替换：如果原计划涉及受伤部位，请从候选项目中挑选一个不涉及该部位的项目作为替代。
                3. 恢复建议：如果所有候选项目都会加重伤病，或者用户状态极差，请推荐 RECOVERY 并给出具体恢复建议。
                4. 优先级：原计划 > 合适的替代项目 > 恢复建议。
                5. 对话理解：结合对话历史灵活调整，并在每一轮回复中都返回最终确定的完整 JSON 方案。

                输出要求：
                必须只输出 JSON，禁止包含 Markdown 或额外解释。格式如下：
                {
                  "recommendationType": "BASE_PLAN|ALTERNATIVE|RECOVERY",
                  "recommendedWorkoutDayId": 1,
                  "recommendedContent": "训练内容或恢复建议",
                  "recommendationReason": "对用户的解释，不超过 50 字"
                }
                如果 recommendationType 是 RECOVERY，recommendedWorkoutDayId 填 null。
                """;
    }

    private String buildUserPrompt(TodayStatus todayStatus, WorkoutDay baseDay, List<WorkoutDay> candidates, boolean completedContext) {
        String statusDescription = todayStatus == null ? "" : defaultString(todayStatus.getDescription());
        StringBuilder builder = new StringBuilder();
        builder.append("用户今日状态：").append(statusDescription).append("\n");
        builder.append("原计划训练：ID=").append(baseDay.getId()).append(", 内容=").append(baseDay.getContent()).append("\n");
        if (completedContext) {
            builder.append("当前状态：今日训练已经打卡完成。\n");
            builder.append("请根据用户最后一条消息判断是否需要提议撤回打卡动作。若不是撤回诉求，请返回明确提示。\n");
            return builder.toString();
        }
        builder.append("候选训练项目：\n");
        for (WorkoutDay candidate : candidates) {
            builder.append("- ID=").append(candidate.getId()).append(", 内容=").append(candidate.getContent()).append("\n");
        }
        builder.append("请只从候选项目中选择一个最合适的推荐，或给出恢复建议。");
        return builder.toString();
    }

    private List<ResolvedRoute> resolveRoutes() {
        List<ResolvedRoute> resolvedRoutes = new ArrayList<>();
        if (!CollectionUtils.isEmpty(aiWorkoutProperties.getRoutes())) {
            int index = 0;
            for (AiWorkoutProperties.Route route : aiWorkoutProperties.getRoutes()) {
                ResolvedRoute resolved = resolveRoute(route, index++);
                if (resolved != null) {
                    resolvedRoutes.add(resolved);
                }
            }
        } else {
            ResolvedRoute legacyRoute = buildLegacyRoute();
            if (legacyRoute != null) {
                resolvedRoutes.add(legacyRoute);
            }
        }

        // 排序逻辑：
        // 1. 不在冷却期的路由优先尝试
        // 2. 最近成功过的路由优先，减少每次都从头探测的成本
        // 3. 最后按名称排序，保证顺序稳定、便于排查日志
        resolvedRoutes.sort(Comparator
                .comparing((ResolvedRoute route) -> isCoolingDown(route) ? 1 : 0)
                .thenComparing(route -> isPreferred(route) ? 0 : 1)
                .thenComparing(ResolvedRoute::name));
        log.debug("Resolved AI routes, count={}, routeOrder={}", resolvedRoutes.size(), summarizeRoutes(resolvedRoutes));
        return resolvedRoutes;
    }

    private ResolvedRoute resolveRoute(AiWorkoutProperties.Route route, int index) {
        if (route == null || !route.isEnabled()) {
            log.debug("Skipping AI route at index {} because it is null or disabled", index);
            return null;
        }

        String baseUrl = trimToNull(route.getBaseUrl());
        String chatPath = defaultString(trimToNull(route.getChatPath()), aiWorkoutProperties.getChatPath());
        String model = defaultString(trimToNull(route.getModel()), aiWorkoutProperties.getModel());
        String apiKey = defaultString(trimToNull(route.getApiKey()), trimToNull(defaultApiKey));

        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(chatPath) || !StringUtils.hasText(model) || !StringUtils.hasText(apiKey)) {
            log.warn("Skipping AI route {} because configuration is incomplete, hasBaseUrl={}, hasChatPath={}, hasModel={}, hasApiKey={}",
                    defaultString(trimToNull(route.getName()), "route-" + index),
                    StringUtils.hasText(baseUrl),
                    StringUtils.hasText(chatPath),
                    StringUtils.hasText(model),
                    StringUtils.hasText(apiKey));
            return null;
        }

        ApiType apiType = resolveApiType(route.getApiType(), chatPath, model);
        String routeName = defaultString(trimToNull(route.getName()), "route-" + index);
        // 这里把配置文件里的 Route 转成运行时可直接调用的 ResolvedRoute，
        // 顺便把路径补全、默认值兜底、接口类型推断都收口在这一层。
        log.debug("Resolved AI route {}, apiType={}, url={}, model={}",
                routeName, apiType, joinUrl(baseUrl, normalizePath(chatPath)), model);
        return new ResolvedRoute(routeName, baseUrl, normalizePath(chatPath), model, apiKey, apiType);
    }

    private ResolvedRoute buildLegacyRoute() {
        String apiKey = trimToNull(defaultApiKey);
        if (!StringUtils.hasText(aiWorkoutProperties.getBaseUrl())
                || !StringUtils.hasText(aiWorkoutProperties.getChatPath())
                || !StringUtils.hasText(aiWorkoutProperties.getModel())
                || !StringUtils.hasText(apiKey)) {
            log.warn("Legacy AI route is unavailable because configuration is incomplete");
            return null;
        }

        ApiType apiType = resolveApiType(aiWorkoutProperties.getApiType(), aiWorkoutProperties.getChatPath(), aiWorkoutProperties.getModel());
        ResolvedRoute route = new ResolvedRoute(
                "legacy-default",
                trimToNull(aiWorkoutProperties.getBaseUrl()),
                normalizePath(aiWorkoutProperties.getChatPath()),
                aiWorkoutProperties.getModel(),
                apiKey,
                apiType
        );
        log.debug("Resolved legacy AI route {}, apiType={}, url={}, model={}",
                route.name(), route.apiType(), route.requestUrl(), route.model());
        return route;
    }

    private ApiType resolveApiType(String configuredApiType, String chatPath, String model) {
        // 优先使用显式配置；没配时再根据路径和模型名做启发式判断。
        if (StringUtils.hasText(configuredApiType)) {
            return "ANTHROPIC".equalsIgnoreCase(configuredApiType) ? ApiType.ANTHROPIC : ApiType.OPENAI;
        }
        if (StringUtils.hasText(chatPath) && chatPath.contains("/messages")) {
            return ApiType.ANTHROPIC;
        }
        if (StringUtils.hasText(model) && model.toLowerCase().startsWith("claude")) {
            return ApiType.ANTHROPIC;
        }
        return ApiType.OPENAI;
    }

    private void markRouteFailure(ResolvedRoute route, String reason) {
        long cooldownSeconds = Math.max(1, aiWorkoutProperties.getRouting().getFailureCooldownSeconds());
        Instant cooldownUntil = Instant.now().plusSeconds(cooldownSeconds);
        // 失败后先进入冷却，而不是下一次还立刻打到同一条坏路由上。
        routeCooldownUntil.put(route.name(), cooldownUntil);
        log.warn("AI route {} marked as cooling down for {}s until {}, reason={}",
                route.name(), cooldownSeconds, cooldownUntil, reason);
    }

    private void markRouteSuccess(ResolvedRoute route) {
        routeCooldownUntil.remove(route.name());
        String previousPreferredRoute = preferredRouteName;
        // 成功过的路由会被记成首选，下次优先尝试，减少无意义探测。
        preferredRouteName = route.name();
        log.info("AI preferred route switched from {} to {}", previousPreferredRoute, preferredRouteName);
    }

    private boolean isCoolingDown(ResolvedRoute route) {
        Instant until = routeCooldownUntil.get(route.name());
        return until != null && until.isAfter(Instant.now());
    }

    private boolean isPreferred(ResolvedRoute route) {
        return StringUtils.hasText(preferredRouteName) && preferredRouteName.equals(route.name());
    }

    private RecommendationPayload parseRecommendationPayload(String content) {
        String jsonPayload = extractFirstJsonObject(content);
        if (!StringUtils.hasText(jsonPayload)) {
            log.warn("AI result does not contain a JSON object, content={}", content);
            return null;
        }

        try {
            JSONObject root = JSON.parseObject(jsonPayload);
            if (root == null || root.isEmpty()) {
                log.warn("AI result JSON is not an object, payload={}", jsonPayload);
                return null;
            }

            return new RecommendationPayload(
                    textValue(root, "recommendationType"),
                    textValue(root, "recommendedContent"),
                    textValue(root, "recommendationReason"),
                    longValue(root, "recommendedWorkoutDayId"),
                    parseActionProposal(root)
            );
        } catch (Exception e) {
            log.warn("Failed to parse AI result JSON: {}", e.getMessage());
            return null;
        }
    }

    private WorkoutRecommendationService.ActionProposal parseActionProposal(JSONObject root) {
        if (root == null) {
            return null;
        }
        JSONObject action = root.getJSONObject("action");
        if (action == null || action.isEmpty()) {
            return null;
        }
        String actionType = textValue(action, "actionType");
        if (!ACTION_UNDO_COMPLETE.equals(actionType)) {
            return null;
        }
        String title = defaultString(textValue(action, "title"), "撤回今天的打卡");
        String impact = defaultString(textValue(action, "impact"), "撤回后今日训练会恢复为未完成，并回滚本次推进的训练进度。");
        String confirmText = defaultString(textValue(action, "confirmText"), "确认撤回");
        return new WorkoutRecommendationService.ActionProposal(actionType, title, impact, confirmText, null, null, "card", "ACTION_CONFIRM");
    }

    /**
     * Extract the first complete JSON object from model output so we can tolerate wrappers
     * like markdown fences or extra explanatory text.
     */
    private String extractFirstJsonObject(String content) {
        int objectStart = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;

        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);

            if (escaping) {
                escaping = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaping = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
                continue;
            }
            if (current == '}' && depth > 0) {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    return content.substring(objectStart, i + 1);
                }
            }
        }

        return null;
    }

    private String textValue(JSONObject root, String field) {
        if (root == null || !root.containsKey(field)) {
            return null;
        }
        String value = root.getString(field);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long longValue(JSONObject root, String field) {
        if (root == null || !root.containsKey(field) || root.get(field) == null) {
            return null;
        }
        return root.getLong(field);
    }

    @Override
    public String extractVisibleRecommendationReason(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        RecommendationPayload payload = parseRecommendationPayload(content);
        if (payload != null && StringUtils.hasText(payload.recommendationReason())) {
            return payload.recommendationReason();
        }

        RecommendationReasonStreamParser parser = new RecommendationReasonStreamParser();
        StringBuilder visibleReason = new StringBuilder();
        parser.pushToken(content, visibleReason::append);
        return StringUtils.hasText(visibleReason.toString()) ? visibleReason.toString() : null;
    }

    private RecommendationResult fallbackToBase(WorkoutDay baseDay, boolean fallbackUsed, String reason) {
        return new RecommendationResult(
                baseDay.getId(),
                baseDay.getContent(),
                TYPE_BASE_PLAN,
                reason,
                fallbackUsed,
                null
        );
    }

    private String defaultReason(String reason) {
        return StringUtils.hasText(reason) ? reason : "按当前状态生成的推荐";
    }

    private String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String summarizeRoutes(List<ResolvedRoute> routes) {
        List<String> routeSummaries = new ArrayList<>();
        for (ResolvedRoute route : routes) {
            routeSummaries.add(route.name()
                    + "[apiType=" + route.apiType()
                    + ",preferred=" + isPreferred(route)
                    + ",coolingDown=" + isCoolingDown(route)
                    + ",url=" + route.requestUrl()
                    + ",model=" + route.model() + "]");
        }
        return String.join(" -> ", routeSummaries);
    }

    private String joinUrl(String baseUrl, String chatPath) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + chatPath;
        }
        return baseUrl + chatPath;
    }

    /**
     * Incrementally extracts the JSON string value of "recommendationReason" from streamed model output.
     * This avoids brittle substring matching and correctly handles whitespace and escaped characters.
     */
    static final class RecommendationReasonStreamParser {

        private ParseState state = ParseState.SEARCHING_FIELD;
        private int fieldMatchIndex;
        private final StringBuilder unicodeBuffer = new StringBuilder();

        boolean pushToken(String token, Consumer<String> onToken) {
            if (!StringUtils.hasText(token) || state == ParseState.COMPLETE) {
                return false;
            }

            StringBuilder emitted = new StringBuilder();
            for (int i = 0; i < token.length(); i++) {
                consumeChar(token.charAt(i), emitted);
            }

            if (emitted.isEmpty()) {
                return false;
            }
            onToken.accept(emitted.toString());
            return true;
        }

        private void consumeChar(char current, StringBuilder emitted) {
            switch (state) {
                case SEARCHING_FIELD -> matchFieldName(current);
                case WAITING_FOR_COLON -> consumeColon(current);
                case WAITING_FOR_VALUE_START -> consumeValueStart(current);
                case IN_VALUE -> consumeValueChar(current, emitted);
                case IN_ESCAPE -> consumeEscapeChar(current, emitted);
                case IN_UNICODE_ESCAPE -> consumeUnicodeEscapeChar(current, emitted);
                case COMPLETE -> {
                    // Ignore remaining characters once the field value has been fully extracted.
                }
            }
        }

        private void matchFieldName(char current) {
            if (current == RECOMMENDATION_REASON_FIELD.charAt(fieldMatchIndex)) {
                fieldMatchIndex++;
                if (fieldMatchIndex == RECOMMENDATION_REASON_FIELD.length()) {
                    state = ParseState.WAITING_FOR_COLON;
                    fieldMatchIndex = 0;
                }
                return;
            }

            fieldMatchIndex = current == RECOMMENDATION_REASON_FIELD.charAt(0) ? 1 : 0;
        }

        private void consumeColon(char current) {
            if (Character.isWhitespace(current)) {
                return;
            }
            if (current == ':') {
                state = ParseState.WAITING_FOR_VALUE_START;
                return;
            }
            restartFieldSearch(current);
        }

        private void consumeValueStart(char current) {
            if (Character.isWhitespace(current)) {
                return;
            }
            if (current == '"') {
                state = ParseState.IN_VALUE;
                return;
            }
            restartFieldSearch(current);
        }

        private void consumeValueChar(char current, StringBuilder emitted) {
            if (current == '\\') {
                state = ParseState.IN_ESCAPE;
                return;
            }
            if (current == '"') {
                state = ParseState.COMPLETE;
                return;
            }
            emitted.append(current);
        }

        private void consumeEscapeChar(char current, StringBuilder emitted) {
            state = ParseState.IN_VALUE;
            switch (current) {
                case '"', '\\', '/' -> emitted.append(current);
                case 'b' -> emitted.append('\b');
                case 'f' -> emitted.append('\f');
                case 'n' -> emitted.append('\n');
                case 'r' -> emitted.append('\r');
                case 't' -> emitted.append('\t');
                case 'u' -> {
                    unicodeBuffer.setLength(0);
                    state = ParseState.IN_UNICODE_ESCAPE;
                }
                default -> emitted.append(current);
            }
        }

        private void consumeUnicodeEscapeChar(char current, StringBuilder emitted) {
            if (isHexChar(current)) {
                unicodeBuffer.append(current);
                if (unicodeBuffer.length() == 4) {
                    emitted.append((char) Integer.parseInt(unicodeBuffer.toString(), 16));
                    unicodeBuffer.setLength(0);
                    state = ParseState.IN_VALUE;
                }
                return;
            }

            emitted.append("\\u").append(unicodeBuffer).append(current);
            unicodeBuffer.setLength(0);
            state = ParseState.IN_VALUE;
        }

        private void restartFieldSearch(char current) {
            state = ParseState.SEARCHING_FIELD;
            fieldMatchIndex = 0;
            matchFieldName(current);
        }

        private boolean isHexChar(char current) {
            return (current >= '0' && current <= '9')
                    || (current >= 'a' && current <= 'f')
                    || (current >= 'A' && current <= 'F');
        }

        private enum ParseState {
            SEARCHING_FIELD,
            WAITING_FOR_COLON,
            WAITING_FOR_VALUE_START,
            IN_VALUE,
            IN_ESCAPE,
            IN_UNICODE_ESCAPE,
            COMPLETE
        }
    }

    private record RecommendationPayload(
            String recommendationType,
            String recommendedContent,
            String recommendationReason,
            Long recommendedWorkoutDayId,
            WorkoutRecommendationService.ActionProposal actionProposal
    ) {
    }

    private enum ApiType {
        OPENAI,
        ANTHROPIC
    }

    private record ResolvedRoute(
            String name,
            String baseUrl,
            String chatPath,
            String model,
            String apiKey,
            ApiType apiType
    ) {
        private String requestUrl() {
            if (baseUrl.endsWith("/")) {
                return baseUrl.substring(0, baseUrl.length() - 1) + chatPath;
            }
            return baseUrl + chatPath;
        }
    }
}
