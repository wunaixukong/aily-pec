package com.ailypec.service;

import com.ailypec.dto.today.TodayStatusSubmitRequest;
import com.ailypec.dto.today.TodayWorkoutChatHistoryResponse;
import com.ailypec.dto.today.TodayWorkoutChatItem;
import com.ailypec.dto.today.TodayWorkoutChatRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteResponse;
import com.ailypec.dto.today.TodayWorkoutRecommendationResponse;
import com.ailypec.entity.ProgressPointer;
import com.ailypec.entity.TodayStatus;
import com.ailypec.entity.TodayWorkoutRecommendation;
import com.ailypec.entity.WorkoutDay;
import com.ailypec.entity.WorkoutPlan;
import com.ailypec.entity.WorkoutRecord;
import com.ailypec.repository.ProgressPointerRepository;
import com.ailypec.repository.TodayStatusRepository;
import com.ailypec.repository.TodayWorkoutRecommendationRepository;
import com.ailypec.repository.WorkoutDayRepository;
import com.ailypec.repository.WorkoutPlanRepository;
import com.ailypec.repository.WorkoutRecordRepository;
import com.ailypec.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TodayWorkoutService {

    private static final String COMPLETION_MODE_BASE_PLAN = "BASE_PLAN";
    private static final String COMPLETION_MODE_RECOMMENDED = "RECOMMENDED";
    private static final String COMPLETION_MODE_CUSTOM = "CUSTOM";
    private static final String RECOMMENDATION_TYPE_RECOVERY = "RECOVERY";

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutDayRepository workoutDayRepository;
    private final ProgressPointerRepository progressPointerRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final TodayStatusRepository todayStatusRepository;
    private final TodayWorkoutRecommendationRepository todayWorkoutRecommendationRepository;
    private final TodayWorkoutChatSessionService todayWorkoutChatSessionService;
    private final WorkoutRecommendationService workoutRecommendationService;

    private final ProgressPointerService progressPointerService;

    /**
     * 保存用户当天的状态描述，并清理当天未完成的推荐快照及其对话历史。
     */
    @Transactional
    public Result<String> submitTodayStatus(Long userId, TodayStatusSubmitRequest request) {
        if (request == null || !StringUtils.hasText(request.getDescription())) {
            return Result.fail("状态描述不能为空");
        }
        LocalDate today = LocalDate.now();

        // 查找并删除旧的推荐快照及其关联的对话消息
        todayWorkoutRecommendationRepository.findByUserIdAndRecommendationDateAndCompletedFalse(userId, today)
                .forEach(rec -> {
                    todayWorkoutChatSessionService.deleteSession(rec.getId());
                    todayWorkoutRecommendationRepository.delete(rec);
                });

        TodayStatus status = todayStatusRepository
                .findFirstByUserIdAndStatusDateOrderByCreateTimeDesc(userId, today)
                .orElseGet(TodayStatus::new);
        status.setUserId(userId);
        status.setStatusDate(today);
        status.setDescription(request.getDescription().trim());
        todayStatusRepository.save(status);
        return Result.success("今日状态已保存");
    }

    /**
     * 获取用户当天的训练推荐，并优先复用当天已生成的推荐快照。
     * 若生成新快照，则自动记录第一条对话历史。
     */
    @Transactional
    public Result<TodayWorkoutRecommendationResponse> getTodayWorkout(Long userId) {
        WorkoutContext context = buildWorkoutContext(userId);
        if (context.errorResult != null) {
            return context.errorResult;
        }

        LocalDate today = LocalDate.now();
        Optional<TodayWorkoutRecommendation> existingRecommendation = todayWorkoutRecommendationRepository
                .findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(userId, today);
        if (existingRecommendation.isPresent()) {
            return Result.success(toRecommendationResponse(existingRecommendation.get()));
        }

        TodayStatus todayStatus = todayStatusRepository
                .findFirstByUserIdAndStatusDateOrderByCreateTimeDesc(userId, today)
                .orElse(null);

        // 调用 AI 生成首轮推荐（不带历史）
        WorkoutRecommendationService.RecommendationResult recommendationResult = workoutRecommendationService.recommend(
                todayStatus,
                null,
                context.baseDay,
                context.days,
                context.currentIndex
        );

        TodayWorkoutRecommendation recommendation = new TodayWorkoutRecommendation();
        recommendation.setUserId(userId);
        recommendation.setPlanId(context.activePlan.getId());
        recommendation.setRecommendationDate(today);
        recommendation.setBaseWorkoutDayId(context.baseDay.getId());
        recommendation.setBaseContent(context.baseDay.getContent());
        recommendation.setRecommendedWorkoutDayId(recommendationResult.recommendedWorkoutDayId());
        recommendation.setRecommendedContent(recommendationResult.recommendedContent());
        recommendation.setRecommendationType(recommendationResult.recommendationType());
        recommendation.setRecommendationReason(recommendationResult.recommendationReason());
        recommendation.setStatusDescriptionSnapshot(todayStatus == null ? null : todayStatus.getDescription());
        recommendation.setFallbackUsed(recommendationResult.fallbackUsed());
        recommendation.setCompleted(false);
        TodayWorkoutRecommendation saved = todayWorkoutRecommendationRepository.save(recommendation);

        // 记录首轮对话历史
        if (todayStatus != null) {
            saveChatMessage(saved.getId(), "user", todayStatus.getDescription());
        }
        saveChatMessage(saved.getId(), "assistant", recommendationResult.recommendationReason());

        return Result.success(toRecommendationResponse(saved));
    }

    /**
     * 与 AI 进行多轮对话，动态调整训练方案。
     */
    @Transactional
    public Result<TodayWorkoutRecommendationResponse> chatTodayWorkout(Long userId, TodayWorkoutChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return Result.fail("回复内容不能为空");
        }

        LocalDate today = LocalDate.now();
        TodayWorkoutRecommendation recommendation = todayWorkoutRecommendationRepository
                .findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(userId, today)
                .orElse(null);

        if (recommendation == null) {
            return Result.fail("请先获取今日推荐方案");
        }
        if (Boolean.TRUE.equals(recommendation.getCompleted())) {
            return Result.fail("今日训练已完成，无法继续对话");
        }

        WorkoutContext context = buildWorkoutContext(userId);
        if (context.errorResult != null) {
            return context.errorResult;
        }

        // 1. 保存用户消息
        saveChatMessage(recommendation.getId(), "user", request.getMessage().trim());

        // 2. 加载全量历史
        List<TodayWorkoutChatItem> history = todayWorkoutChatSessionService.getMessages(recommendation.getId());

        // 3. 调用 AI 获取新方案
        WorkoutRecommendationService.RecommendationResult recommendationResult = workoutRecommendationService.recommend(
                null,
                history,
                context.baseDay,
                context.days,
                context.currentIndex
        );

        // 4. 保存 AI 消息
        saveChatMessage(recommendation.getId(), "assistant", recommendationResult.recommendationReason());

        // 5. 更新推荐快照（同步最新方案）
        recommendation.setRecommendedWorkoutDayId(recommendationResult.recommendedWorkoutDayId());
        recommendation.setRecommendedContent(recommendationResult.recommendedContent());
        recommendation.setRecommendationType(recommendationResult.recommendationType());
        recommendation.setRecommendationReason(recommendationResult.recommendationReason());
        recommendation.setFallbackUsed(recommendationResult.fallbackUsed());
        todayWorkoutRecommendationRepository.save(recommendation);

        return Result.success(toRecommendationResponse(recommendation));
    }

    /**
     * 与 AI 进行流式多轮对话。
     */
    public SseEmitter chatTodayWorkoutStream(Long userId, TodayWorkoutChatRequest request) {
        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时

        if (request == null || !StringUtils.hasText(request.getMessage())) {
            try {
                emitter.send(SseEmitter.event().name("error").data("回复内容不能为空"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        LocalDate today = LocalDate.now();
        TodayWorkoutRecommendation recommendation = todayWorkoutRecommendationRepository
                .findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(userId, today)
                .orElse(null);

        if (recommendation == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("请先获取今日推荐方案"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }
        if (Boolean.TRUE.equals(recommendation.getCompleted())) {
            try {
                emitter.send(SseEmitter.event().name("error").data("今日训练已完成，无法继续对话"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        WorkoutContext context = buildWorkoutContext(userId);
        if (context.errorResult != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(context.errorResult.getMessage()));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // 1. 保存用户消息 (同步)
        saveChatMessage(recommendation.getId(), "user", request.getMessage().trim());

        // 2. 加载全量历史
        List<TodayWorkoutChatItem> history = todayWorkoutChatSessionService.getMessages(recommendation.getId());

        // 3. 调用 AI 流式接口 (异步)
        workoutRecommendationService.recommendStream(
                null,
                history,
                context.baseDay,
                context.days,
                context.currentIndex,
                (token) -> {
                    try {
                        emitter.send(SseEmitter.event().name("message").data(token));
                    } catch (IOException e) {
                        // 前端断开连接
                    }
                },
                (fullContent) -> {
                    if (fullContent == null) {
                        try {
                            emitter.send(SseEmitter.event().name("error").data("AI 响应异常"));
                            emitter.complete();
                        } catch (IOException ignored) {}
                        return;
                    }

                    try {
                        // 4. 解析完整结果并持久化
                        updateRecommendationFromFinalResult(recommendation.getId(), fullContent, context.baseDay, context.days);
                        emitter.send(SseEmitter.event().name("done").data("complete"));
                        emitter.complete();
                    } catch (Exception e) {
                        try {
                            emitter.send(SseEmitter.event().name("error").data("更新方案失败"));
                            emitter.complete();
                        } catch (IOException ignored) {}
                    }
                }
        );

        return emitter;
    }

    /**
     * 在流结束后，解析 AI 返回的完整内容并更新数据库（需独立开启事务）。
     */
    @Transactional
    public void updateRecommendationFromFinalResult(Long recommendationId, String fullContent, WorkoutDay baseDay, List<WorkoutDay> orderedDays) {
        TodayWorkoutRecommendation recommendation = todayWorkoutRecommendationRepository.findById(recommendationId).orElse(null);
        if (recommendation == null) return;

        WorkoutRecommendationService.RecommendationResult result = workoutRecommendationService.parseResult(fullContent, baseDay, orderedDays);
        if (result != null) {
            saveChatMessage(recommendation.getId(), "assistant", result.recommendationReason());
            recommendation.setRecommendedWorkoutDayId(result.recommendedWorkoutDayId());
            recommendation.setRecommendedContent(result.recommendedContent());
            recommendation.setRecommendationType(result.recommendationType());
            recommendation.setRecommendationReason(result.recommendationReason());
            recommendation.setFallbackUsed(result.fallbackUsed());
            todayWorkoutRecommendationRepository.save(recommendation);
            return;
        }

        String visibleReason = workoutRecommendationService.extractVisibleRecommendationReason(fullContent);
        if (StringUtils.hasText(visibleReason)) {
            saveChatMessage(recommendation.getId(), "assistant", visibleReason);
        }
    }

    private void saveChatMessage(Long recommendationId, String role, String content) {
        todayWorkoutChatSessionService.appendMessage(recommendationId, role, content);
    }

    public Result<TodayWorkoutChatHistoryResponse> getTodayWorkoutChatHistory(Long userId) {
        TodayWorkoutRecommendation recommendation = todayWorkoutRecommendationRepository
                .findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(userId, LocalDate.now())
                .orElse(null);
        if (recommendation == null) {
            return Result.fail("璇峰厛鑾峰彇浠婃棩鎺ㄨ崘鏂规");
        }

        return Result.success(todayWorkoutChatSessionService.getHistory(recommendation.getId()));
    }

    /**
     * 按推荐快照提交当天训练完成结果，并根据完成模式决定是否推进指针。
     */
    @Transactional
    public Result<TodayWorkoutCompleteResponse> completeTodayWorkout(Long userId, TodayWorkoutCompleteRequest request) {
        if (request == null) {
            request = new TodayWorkoutCompleteRequest();
        }
        if (request.getRecommendationId() == null) {
            TodayWorkoutRecommendation existingRecommendation = todayWorkoutRecommendationRepository
                    .findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(userId, LocalDate.now())
                    .orElse(null);
            if (existingRecommendation == null) {
                return Result.fail("recommendationId 不能为空");
            }
            request.setRecommendationId(existingRecommendation.getId());
        }

        WorkoutContext context = buildWorkoutContext(userId);
        if (context.errorResult != null) {
            return context.errorResultComplete();
        }

        LocalDate today = LocalDate.now();
        List<WorkoutRecord> todayRecords = workoutRecordRepository.findByUserIdAndWorkoutDate(userId, today);
        if (!todayRecords.isEmpty()) {
            return Result.fail("今天已经完成训练了，明天再来吧！");
        }

        TodayWorkoutRecommendation recommendation = todayWorkoutRecommendationRepository
                .findByIdAndUserId(request.getRecommendationId(), userId)
                .orElse(null);
        if (recommendation == null || !today.equals(recommendation.getRecommendationDate())) {
            return Result.fail("今日推荐不存在或已失效");
        }
        if (Boolean.TRUE.equals(recommendation.getCompleted())) {
            return Result.fail("今天已经完成训练了，明天再来吧！");
        }

        CompletionSelection selection = resolveCompletionSelection(request, recommendation, context.days, context.baseDay);
        if (selection == null) {
            return Result.fail("完成内容不合法");
        }

        boolean pointerAdvanced = shouldAdvancePointer(selection, recommendation);
        Integer nextIndex = context.pointer.getCurrentDayIndex();
        String nextWorkoutContent = context.baseDay.getContent();
        if (pointerAdvanced) {
            nextIndex = (context.pointer.getCurrentDayIndex() + 1) % context.days.size();
            context.pointer.setCurrentDayIndex(nextIndex);
            context.pointer.setLastWorkoutDate(LocalDateTime.now());
            progressPointerRepository.save(context.pointer);
            nextWorkoutContent = context.days.get(nextIndex).getContent();
        }

        WorkoutRecord record = new WorkoutRecord();
        record.setUserId(userId);
        record.setPlanId(context.activePlan.getId());
        record.setWorkoutDayId(selection.workoutDayId());
        record.setContent(selection.content());
        record.setRecommendationId(recommendation.getId());
        record.setBaseWorkoutDayId(recommendation.getBaseWorkoutDayId());
        record.setCompletionMode(selection.completionMode());
        record.setPointerAdvanced(pointerAdvanced);
        record.setStatusDescriptionSnapshot(recommendation.getStatusDescriptionSnapshot());
        record.setRecommendationReasonSnapshot(recommendation.getRecommendationReason());
        record.setRecommendedWorkoutDayId(recommendation.getRecommendedWorkoutDayId());
        record.setRecommendedContent(recommendation.getRecommendedContent());
        record.setWorkoutDate(today);
        WorkoutRecord savedRecord = workoutRecordRepository.save(record);

        recommendation.setCompleted(true);
        todayWorkoutRecommendationRepository.save(recommendation);

        TodayWorkoutCompleteResponse response = new TodayWorkoutCompleteResponse();
        response.setRecordId(savedRecord.getId());
        response.setRecommendationId(recommendation.getId());
        response.setCompletionMode(selection.completionMode());
        response.setPointerAdvanced(pointerAdvanced);
        response.setNextDayIndex(nextIndex);
        response.setNextWorkoutContent(nextWorkoutContent);
        response.setCompletedWorkoutDayId(selection.workoutDayId());
        response.setCompletedContent(selection.content());
        return Result.success(response);
    }

    /**
     * 初始化训练进度指针。
     */
    @Transactional
    public Result<Boolean> initProgress(Long userId, Long planId) {
        Result<Boolean> result = Result.create();
        if (progressPointerRepository.findByUserId(userId).isEmpty()) {
            ProgressPointer pointer = new ProgressPointer();
            pointer.setUserId(userId);
            pointer.setActivePlanId(planId);
            pointer.setCurrentDayIndex(0);
            progressPointerRepository.save(pointer);
            result.setMessage("进度指针初始化成功");
        } else {
            result.setMessage("进度指针已存在");
        }
        return result;
    }

    /**
     * 根据请求内容和推荐快照确定本次实际完成的训练内容。
     */
    private CompletionSelection resolveCompletionSelection(TodayWorkoutCompleteRequest request,
                                                           TodayWorkoutRecommendation recommendation,
                                                           List<WorkoutDay> days,
                                                           WorkoutDay baseDay) {
        String completionMode = normalizeCompletionMode(request.getCompletionMode(), recommendation, request.getCompletedWorkoutDayId());
        if (COMPLETION_MODE_CUSTOM.equals(completionMode)) {
            if (!StringUtils.hasText(request.getCompletedContent())) {
                return null;
            }
            return new CompletionSelection(request.getCompletedWorkoutDayId(), request.getCompletedContent().trim(), COMPLETION_MODE_CUSTOM);
        }
        if (COMPLETION_MODE_BASE_PLAN.equals(completionMode)) {
            return new CompletionSelection(baseDay.getId(), baseDay.getContent(), COMPLETION_MODE_BASE_PLAN);
        }
        if (RECOMMENDATION_TYPE_RECOVERY.equals(recommendation.getRecommendationType())) {
            return new CompletionSelection(null, recommendation.getRecommendedContent(), COMPLETION_MODE_RECOMMENDED);
        }
        Optional<WorkoutDay> recommendedDay = workoutRecommendationService.findRecommendedDayById(days, recommendation.getRecommendedWorkoutDayId());
        if (recommendedDay.isEmpty() && recommendation.getRecommendedWorkoutDayId() != null) {
            recommendedDay = days.stream()
                    .filter(item -> recommendation.getRecommendedWorkoutDayId().equals(item.getId()))
                    .findFirst();
        }
        if (recommendedDay.isEmpty()) {
            return null;
        }
        WorkoutDay day = recommendedDay.get();
        return new CompletionSelection(day.getId(), day.getContent(), COMPLETION_MODE_RECOMMENDED);
    }

    /**
     * 判断本次完成结果是否应推进训练指针。
     */
    private boolean shouldAdvancePointer(CompletionSelection selection, TodayWorkoutRecommendation recommendation) {
        if (COMPLETION_MODE_CUSTOM.equals(selection.completionMode())) {
            return false;
        }
        if (RECOMMENDATION_TYPE_RECOVERY.equals(recommendation.getRecommendationType())) {
            return false;
        }
        return COMPLETION_MODE_BASE_PLAN.equals(selection.completionMode())
                || COMPLETION_MODE_RECOMMENDED.equals(selection.completionMode());
    }

    /**
     * 归一化本次训练完成模式。
     */
    private String normalizeCompletionMode(String requestedMode,
                                           TodayWorkoutRecommendation recommendation,
                                           Long completedWorkoutDayId) {
        if (StringUtils.hasText(requestedMode)) {
            return requestedMode.trim().toUpperCase();
        }
        if (completedWorkoutDayId == null && RECOMMENDATION_TYPE_RECOVERY.equals(recommendation.getRecommendationType())) {
            return COMPLETION_MODE_RECOMMENDED;
        }
        if (completedWorkoutDayId == null) {
            return COMPLETION_MODE_RECOMMENDED;
        }
        if (completedWorkoutDayId != null && completedWorkoutDayId.equals(recommendation.getBaseWorkoutDayId())) {
            return COMPLETION_MODE_BASE_PLAN;
        }
        if (completedWorkoutDayId != null && completedWorkoutDayId.equals(recommendation.getRecommendedWorkoutDayId())) {
            return COMPLETION_MODE_RECOMMENDED;
        }
        return COMPLETION_MODE_CUSTOM;
    }

    /**
     * 构建 today 主链路所需的训练上下文。
     */
    private WorkoutContext buildWorkoutContext(Long userId) {
        List<WorkoutPlan> plans = workoutPlanRepository.findByUserIdAndIsActiveTrue(userId);
        if (CollectionUtils.isEmpty(plans)) {
            return WorkoutContext.error(Result.fail("没有激活的训练计划"));
        }
        WorkoutPlan activePlan = plans.stream()
                .sorted(Comparator.comparing(WorkoutPlan::getCreateTime))
                .findFirst()
                .orElse(null);
        if (activePlan == null) {
            return WorkoutContext.error(Result.fail("没有激活的训练计划"));
        }

        ProgressPointer pointer = progressPointerService.findByActivePlanId(activePlan.getId());
        if (pointer == null) {
            return WorkoutContext.error(Result.fail("进度指针未初始化"));
        }

        List<WorkoutDay> days = workoutDayRepository.findByWorkoutPlanIdOrderByDayOrderAsc(activePlan.getId());
        if (days.isEmpty()) {
            return WorkoutContext.error(Result.fail("训练计划为空"));
        }

        int currentIndex = pointer.getCurrentDayIndex() % days.size();
        WorkoutDay baseDay = days.get(currentIndex);
        return WorkoutContext.success(activePlan, pointer, days, currentIndex, baseDay);
    }

    /**
     * 将推荐快照实体转换为接口响应对象。
     */
    private TodayWorkoutRecommendationResponse toRecommendationResponse(TodayWorkoutRecommendation recommendation) {
        TodayWorkoutRecommendationResponse response = new TodayWorkoutRecommendationResponse();
        response.setRecommendationId(recommendation.getId());
        response.setPlanId(recommendation.getPlanId());
        response.setBaseWorkoutDayId(recommendation.getBaseWorkoutDayId());
        response.setBaseContent(recommendation.getBaseContent());
        response.setRecommendedWorkoutDayId(recommendation.getRecommendedWorkoutDayId());
        response.setRecommendedContent(recommendation.getRecommendedContent());
        response.setRecommendationType(recommendation.getRecommendationType());
        response.setRecommendationReason(recommendation.getRecommendationReason());
        response.setStatusDescription(recommendation.getStatusDescriptionSnapshot());
        response.setFallbackUsed(recommendation.getFallbackUsed());
        response.setCompleted(recommendation.getCompleted());
        return response;
    }

    private record CompletionSelection(Long workoutDayId, String content, String completionMode) {
    }

    private static class WorkoutContext {
        private final WorkoutPlan activePlan;
        private final ProgressPointer pointer;
        private final List<WorkoutDay> days;
        private final int currentIndex;
        private final WorkoutDay baseDay;
        private final Result<TodayWorkoutRecommendationResponse> errorResult;

        private WorkoutContext(WorkoutPlan activePlan,
                               ProgressPointer pointer,
                               List<WorkoutDay> days,
                               int currentIndex,
                               WorkoutDay baseDay,
                               Result<TodayWorkoutRecommendationResponse> errorResult) {
            this.activePlan = activePlan;
            this.pointer = pointer;
            this.days = days;
            this.currentIndex = currentIndex;
            this.baseDay = baseDay;
            this.errorResult = errorResult;
        }

        private static WorkoutContext success(WorkoutPlan activePlan,
                                              ProgressPointer pointer,
                                              List<WorkoutDay> days,
                                              int currentIndex,
                                              WorkoutDay baseDay) {
            return new WorkoutContext(activePlan, pointer, days, currentIndex, baseDay, null);
        }

        private static WorkoutContext error(Result<TodayWorkoutRecommendationResponse> errorResult) {
            return new WorkoutContext(null, null, null, 0, null, errorResult);
        }

        private Result<TodayWorkoutCompleteResponse> errorResultComplete() {
            return Result.fail(errorResult.getMessage());
        }
    }
}
