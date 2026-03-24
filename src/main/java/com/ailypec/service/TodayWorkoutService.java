package com.ailypec.service;

import com.ailypec.dto.today.TodayStatusSubmitRequest;
import com.ailypec.dto.today.TodayWorkoutActionExecuteRequest;
import com.ailypec.dto.today.TodayWorkoutActionExecuteResponse;
import com.ailypec.dto.today.TodayWorkoutActionRequestMeta;
import com.ailypec.dto.today.TodayWorkoutCardAction;
import com.ailypec.dto.today.TodayWorkoutCardBlockData;
import com.ailypec.dto.today.TodayWorkoutChatHistoryResponse;
import com.ailypec.dto.today.TodayWorkoutChatItem;
import com.ailypec.dto.today.TodayWorkoutChatRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteResponse;
import com.ailypec.dto.today.TodayWorkoutRecommendationResponse;
import com.ailypec.dto.today.TodayWorkoutRenderBlock;
import com.ailypec.dto.today.TodayWorkoutStreamDoneResponse;
import com.ailypec.dto.today.TodayWorkoutTextBlockData;
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
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TodayWorkoutService {

    private static final String COMPLETION_MODE_BASE_PLAN = "BASE_PLAN";
    private static final String COMPLETION_MODE_RECOMMENDED = "RECOMMENDED";
    private static final String COMPLETION_MODE_CUSTOM = "CUSTOM";
    private static final String RECOMMENDATION_TYPE_RECOVERY = "RECOVERY";
    private static final String ACTION_UNDO_COMPLETE = "UNDO_COMPLETE";
    private static final String ACTION_CANCEL = "CANCEL";
    private static final String REVOKED_BY_AI_ACTION = "AI_ACTION";
    private static final String UNDO_SUCCESS_MESSAGE = "已为你撤回今天的打卡";
    private static final String COMPLETED_CHAT_GUIDANCE = "今日已打卡，如为误触可让我帮你撤回";
    private static final String ACTION_EXECUTE_PATH_TEMPLATE = "/message/%s/actions/execute";
    private static final java.util.regex.Pattern OPERATION_INTENT_PATTERN = java.util.regex.Pattern.compile(".*(撤回|取消|删掉|重置|误触).*(打卡|记录|完成|训练|方案).*|.*(打卡|记录|完成|训练|方案).*(撤回|取消|删掉|重置|误触).*");

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutDayRepository workoutDayRepository;
    private final ProgressPointerRepository progressPointerRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final TodayStatusRepository todayStatusRepository;
    private final TodayWorkoutRecommendationRepository todayWorkoutRecommendationRepository;
    private final TodayWorkoutChatSessionService todayWorkoutChatSessionService;
    private final WorkoutRecommendationService workoutRecommendationService;
    private final ProgressPointerService progressPointerService;

    @Transactional
    public Result<String> submitTodayStatus(Long userId, TodayStatusSubmitRequest request) {
        if (request == null || !StringUtils.hasText(request.getDescription())) {
            return Result.fail("状态描述不能为空");
        }
        LocalDate today = LocalDate.now();

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

        WorkoutRecommendationService.RecommendationResult recommendationResult = workoutRecommendationService.recommend(
                todayStatus,
                null,
                context.baseDay,
                context.days,
                context.currentIndex,
                false
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

        if (todayStatus != null) {
            saveChatMessage(saved.getId(), "user", todayStatus.getDescription());
        }
        saveChatMessage(saved.getId(), "assistant", recommendationResult.recommendationReason());
        todayWorkoutChatSessionService.clearPendingBlocks(saved.getId());

        return Result.success(toRecommendationResponse(saved));
    }

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

        WorkoutContext context = buildWorkoutContext(userId);
        if (context.errorResult != null) {
            return context.errorResult;
        }

        boolean completedContext = Boolean.TRUE.equals(recommendation.getCompleted());
        saveChatMessage(recommendation.getId(), "user", request.getMessage().trim());
        List<TodayWorkoutChatItem> history = todayWorkoutChatSessionService.getMessages(recommendation.getId());

        WorkoutRecommendationService.RecommendationResult recommendationResult = workoutRecommendationService.recommend(
                null,
                history,
                context.baseDay,
                context.days,
                context.currentIndex,
                completedContext
        );

        return Result.success(applyChatRecommendationResult(recommendation, recommendationResult, completedContext));
    }

    public SseEmitter chatTodayWorkoutStream(Long userId, TodayWorkoutChatRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);

        if (request == null || !StringUtils.hasText(request.getMessage())) {
            try {
                emitter.send(SseEmitter.event().name("error").data("回复内容不能为空"));
                emitter.complete();
            } catch (IOException ignored) {
            }
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
            } catch (IOException ignored) {
            }
            return emitter;
        }

        WorkoutContext context = buildWorkoutContext(userId);
        if (context.errorResult != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(context.errorResult.getMessage()));
                emitter.complete();
            } catch (IOException ignored) {
            }
            return emitter;
        }

        boolean completedContext = Boolean.TRUE.equals(recommendation.getCompleted());
        saveChatMessage(recommendation.getId(), "user", request.getMessage().trim());
        List<TodayWorkoutChatItem> history = todayWorkoutChatSessionService.getMessages(recommendation.getId());

        // 异步预取 recordId，避免在 AI 响应结束后的“静默期”再去查 DB 导致前端超时
        CompletableFuture<Long> recordIdFuture = CompletableFuture.supplyAsync(() ->
                workoutRecordRepository.findFirstByUserIdAndRecommendationIdAndWorkoutDateOrderByCreateTimeDesc(
                                userId, recommendation.getId(), today)
                        .map(WorkoutRecord::getId)
                        .orElse(null)
        );

        boolean isOperationIntent = isOperationIntent(request.getMessage());
        log.info("Processing chat stream: userId={}, intent={}, completedContext={}", userId, isOperationIntent ? "OPERATION" : "NORMAL", completedContext);

        if (isOperationIntent && completedContext) {
            // 如果已打卡且意图是操作类，直接由系统返回撤回提议，不再询问 AI，保证 100% 成功率和零延迟
            handleSystemOperationProposal(emitter, recommendation, recordIdFuture, context);
            return emitter;
        }

        if (isOperationIntent) {
            workoutRecommendationService.recommendOperationStream(
                    history,
                    context.baseDay,
                    completedContext,
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().name("message").data(token));
                        } catch (IOException ignored) {
                        }
                    },
                    fullContent -> handleAiRecommendationDone(emitter, recommendation, fullContent, context, completedContext, recordIdFuture)
            );
        } else {
            workoutRecommendationService.recommendStream(
                    null,
                    history,
                    context.baseDay,
                    context.days,
                    context.currentIndex,
                    completedContext,
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().name("message").data(token));
                        } catch (IOException ignored) {
                        }
                    },
                    fullContent -> handleAiRecommendationDone(emitter, recommendation, fullContent, context, completedContext, recordIdFuture)
            );
        }

        return emitter;
    }

    private void handleSystemOperationProposal(SseEmitter emitter,
                                               TodayWorkoutRecommendation recommendation,
                                               CompletableFuture<Long> recordIdFuture,
                                               WorkoutContext context) {
        String msg = "好的，没问题，我这就帮你撤回今天的打卡记录。你可以重新选择方案或稍后再次打卡。";
        try {
            // 1. 模拟流式输出
            for (char c : msg.toCharArray()) {
                emitter.send(SseEmitter.event().name("message").data(String.valueOf(c)));
            }

            // 2. 构造 ActionProposal
            Long recordId = recordIdFuture.getNow(null);
            WorkoutRecommendationService.ActionProposal proposal = new WorkoutRecommendationService.ActionProposal(
                    ACTION_UNDO_COMPLETE,
                    "撤回今天的打卡",
                    "撤回后今日训练会恢复为未完成，并回滚本次推进的训练进度。",
                    "确认撤回",
                    recommendation.getId(),
                    recordId,
                    "card",
                    "ACTION_CONFIRM"
            );

            // 3. 构建结果块
            List<TodayWorkoutRenderBlock> blocks = buildBlocks(recommendation.getUserId(), msg, proposal);
            todayWorkoutChatSessionService.setPendingBlocks(recommendation.getId(), extractNonTextBlocks(blocks));

            TodayWorkoutRecommendationResponse response = toRecommendationResponse(recommendation);
            response.setRecommendationReason(msg);
            response.setBlocks(blocks);

            TodayWorkoutStreamDoneResponse doneResponse = new TodayWorkoutStreamDoneResponse(msg, response, blocks);

            // 4. 发送 done
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name("done").data(doneResponse));
                Thread.sleep(100);
                emitter.complete();
            }
            saveChatMessage(recommendation.getId(), "assistant", msg, blocks);
        } catch (Exception e) {
            log.error("Failed to send system operation proposal", e);
            emitter.complete();
        }
    }

    private void handleAiRecommendationDone(SseEmitter emitter,
                                            TodayWorkoutRecommendation recommendation,
                                            String fullContent,
                                            WorkoutContext context,
                                            boolean completedContext,
                                            CompletableFuture<Long> recordIdFuture) {
        if (fullContent == null) {
            try {
                emitter.completeWithError(new RuntimeException("AI 响应异常"));
            } catch (Exception ignored) {
            }
            return;
        }

        try {
            Long preloadedRecordId = null;
            try {
                // 此时 AI 已经吐完 token，异步任务早已完成，直接 get 即可
                preloadedRecordId = recordIdFuture.getNow(null);
            } catch (Exception e) {
                log.warn("Failed to get preloaded recordId: {}", e.getMessage());
            }

            TodayWorkoutStreamDoneResponse doneResponse = updateRecommendationFromFinalResult(
                    recommendation.getId(),
                    fullContent,
                    context.baseDay,
                    context.days,
                    completedContext,
                    preloadedRecordId
            );

            // 使用 synchronized 确保同一时间只有一个线程操作 emitter
            synchronized (emitter) {
                try {
                    emitter.send(SseEmitter.event().name("done").data(doneResponse));
                    // 稍微延迟关闭，给网络缓冲区留出时间
                    Thread.sleep(100);
                    emitter.complete();
                } catch (Exception e) {
                    log.warn("Failed to send done event to SSE emitter: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error processing AI recommendation completion", e);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isOperationIntent(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String msg = message.toLowerCase();
        // 操作词：想要改变现状
        boolean hasAction = msg.contains("撤回") || msg.contains("撤销") || msg.contains("取消")
                || msg.contains("删掉") || msg.contains("删除") || msg.contains("重置")
                || msg.contains("误触") || msg.contains("点错") || msg.contains("重新");

        // 对象词：针对的是打卡记录
        boolean hasObject = msg.contains("打卡") || msg.contains("记录") || msg.contains("完成")
                || msg.contains("训练") || msg.contains("方案") || msg.contains("内容");

        // 特殊快捷短语
        boolean quickMatch = msg.contains("帮我撤回") || msg.contains("帮我取消") || msg.contains("写错了") || msg.contains("重来");

        return (hasAction && hasObject) || quickMatch;
    }

    @Transactional
    public TodayWorkoutStreamDoneResponse updateRecommendationFromFinalResult(Long recommendationId,
                                                                              String fullContent,
                                                                              WorkoutDay baseDay,
                                                                              List<WorkoutDay> orderedDays,
                                                                              boolean completedContext,
                                                                              Long preloadedRecordId) {
        log.info("AI Final Content for recommendation {}: {}", recommendationId, fullContent);
        TodayWorkoutRecommendation recommendation = todayWorkoutRecommendationRepository.findById(recommendationId).orElse(null);
        if (recommendation == null) {
            return new TodayWorkoutStreamDoneResponse(null, null, List.of());
        }

        WorkoutRecommendationService.RecommendationResult result = workoutRecommendationService.parseResult(fullContent, baseDay, orderedDays);
        if (result != null) {
            TodayWorkoutRecommendationResponse response = applyChatRecommendationResult(recommendation, result, completedContext, preloadedRecordId);
            return new TodayWorkoutStreamDoneResponse(result.recommendationReason(), response, response.getBlocks());
        }

        String visibleReason = workoutRecommendationService.extractVisibleRecommendationReason(fullContent);
        if (StringUtils.hasText(visibleReason)) {
            saveChatMessage(recommendation.getId(), "assistant", visibleReason);
        }
        return new TodayWorkoutStreamDoneResponse(visibleReason, toRecommendationResponse(recommendation), buildTextBlocks(visibleReason));
    }

    @Transactional
    public TodayWorkoutStreamDoneResponse updateRecommendationFromFinalResult(Long recommendationId,
                                                                              String fullContent,
                                                                              WorkoutDay baseDay,
                                                                              List<WorkoutDay> orderedDays,
                                                                              boolean completedContext) {
        return updateRecommendationFromFinalResult(recommendationId, fullContent, baseDay, orderedDays, completedContext, null);
    }

    public Result<TodayWorkoutChatHistoryResponse> getTodayWorkoutChatHistory(Long userId) {
        TodayWorkoutRecommendation recommendation = todayWorkoutRecommendationRepository
                .findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(userId, LocalDate.now())
                .orElse(null);
        if (recommendation == null) {
            return Result.fail("请先获取今日推荐方案");
        }

        return Result.success(todayWorkoutChatSessionService.getHistory(recommendation.getId()));
    }

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
        List<WorkoutRecord> todayRecords = workoutRecordRepository.findByUserIdAndWorkoutDateAndRevokedFalse(userId, today);
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

        Integer previousDayIndex = context.pointer.getCurrentDayIndex();
        LocalDateTime previousLastWorkoutDate = context.pointer.getLastWorkoutDate();
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
        record.setPointerPreviousDayIndex(previousDayIndex);
        record.setPointerPreviousLastWorkoutDate(previousLastWorkoutDate);
        record.setStatusDescriptionSnapshot(recommendation.getStatusDescriptionSnapshot());
        record.setRecommendationReasonSnapshot(recommendation.getRecommendationReason());
        record.setRecommendedWorkoutDayId(recommendation.getRecommendedWorkoutDayId());
        record.setRecommendedContent(recommendation.getRecommendedContent());
        record.setWorkoutDate(today);
        record.setRevoked(false);
        WorkoutRecord savedRecord = workoutRecordRepository.save(record);

        recommendation.setCompleted(true);
        todayWorkoutRecommendationRepository.save(recommendation);
        todayWorkoutChatSessionService.clearPendingBlocks(recommendation.getId());

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

    @Transactional
    public Result<TodayWorkoutActionExecuteResponse> executeUndoComplete(Long userId, TodayWorkoutActionExecuteRequest request) {
        if (request == null || request.getRecommendationId() == null) {
            return Result.fail("recommendationId 不能为空");
        }

        LocalDate today = LocalDate.now();
        TodayWorkoutRecommendation recommendation = todayWorkoutRecommendationRepository
                .findByIdAndUserId(request.getRecommendationId(), userId)
                .orElse(null);
        if (recommendation == null || !today.equals(recommendation.getRecommendationDate())) {
            return Result.fail("今日推荐不存在或已失效");
        }

        WorkoutRecord record = resolveUndoTargetRecord(userId, request, today);
        if (record == null) {
            return Result.fail("找不到可撤回的今日打卡记录");
        }
        if (!request.getRecommendationId().equals(record.getRecommendationId())) {
            return Result.fail("recommendation 与 record 不匹配");
        }

        if (Boolean.TRUE.equals(record.getRevoked())) {
            TodayWorkoutActionExecuteResponse response = buildUndoExecuteResponse(recommendation, record, true, true, "今天的打卡已是撤回状态");
            todayWorkoutChatSessionService.clearPendingBlocks(recommendation.getId());
            return Result.success(response);
        }

        if (!Boolean.TRUE.equals(recommendation.getCompleted())) {
            TodayWorkoutActionExecuteResponse response = buildUndoExecuteResponse(recommendation, record, false, true, "今天的打卡已撤回，无需重复执行");
            todayWorkoutChatSessionService.clearPendingBlocks(recommendation.getId());
            return Result.success(response);
        }

        WorkoutContext context = buildWorkoutContext(userId);
        if (context.errorResult != null) {
            return Result.fail(context.errorResult.getMessage());
        }

        record.setRevoked(true);
        record.setRevokedTime(LocalDateTime.now());
        record.setRevokedReason("用户通过 AI 确认撤回今日打卡");
        record.setRevokedBy(REVOKED_BY_AI_ACTION);
        workoutRecordRepository.save(record);

        recommendation.setCompleted(false);
        todayWorkoutRecommendationRepository.save(recommendation);

        if (Boolean.TRUE.equals(record.getPointerAdvanced())) {
            context.pointer.setCurrentDayIndex(record.getPointerPreviousDayIndex());
            context.pointer.setLastWorkoutDate(record.getPointerPreviousLastWorkoutDate());
            progressPointerRepository.save(context.pointer);
        }

        todayWorkoutChatSessionService.clearPendingBlocks(recommendation.getId());
        saveChatMessage(recommendation.getId(), "assistant", UNDO_SUCCESS_MESSAGE, buildBlocks(recommendation.getUserId(), UNDO_SUCCESS_MESSAGE, null));

        TodayWorkoutActionExecuteResponse response = buildUndoExecuteResponse(recommendation, record, true, false, UNDO_SUCCESS_MESSAGE);
        return Result.success(response);
    }

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

    private TodayWorkoutRecommendationResponse applyChatRecommendationResult(TodayWorkoutRecommendation recommendation,
                                                                             WorkoutRecommendationService.RecommendationResult recommendationResult,
                                                                             boolean completedContext,
                                                                             Long preloadedRecordId) {
        String assistantMessage = recommendationResult.recommendationReason();
        if (completedContext && recommendationResult.actionProposal() == null) {
            assistantMessage = COMPLETED_CHAT_GUIDANCE;
        }

        List<TodayWorkoutRenderBlock> blocks = buildBlocks(
                recommendation.getUserId(),
                assistantMessage,
                attachActionContext(recommendation, recommendationResult.actionProposal(), preloadedRecordId)
        );
        saveChatMessage(recommendation.getId(), "assistant", assistantMessage, blocks);

        if (completedContext) {
            todayWorkoutChatSessionService.setPendingBlocks(recommendation.getId(), extractNonTextBlocks(blocks));
        } else {
            todayWorkoutChatSessionService.clearPendingBlocks(recommendation.getId());
            recommendation.setRecommendedWorkoutDayId(recommendationResult.recommendedWorkoutDayId());
            recommendation.setRecommendedContent(recommendationResult.recommendedContent());
            recommendation.setRecommendationType(recommendationResult.recommendationType());
            recommendation.setRecommendationReason(assistantMessage);
            recommendation.setFallbackUsed(recommendationResult.fallbackUsed());
            todayWorkoutRecommendationRepository.save(recommendation);
        }

        TodayWorkoutRecommendationResponse response = toRecommendationResponse(recommendation);
        response.setRecommendationReason(assistantMessage);
        response.setBlocks(blocks);
        return response;
    }

    private TodayWorkoutRecommendationResponse applyChatRecommendationResult(TodayWorkoutRecommendation recommendation,
                                                                             WorkoutRecommendationService.RecommendationResult recommendationResult,
                                                                             boolean completedContext) {
        return applyChatRecommendationResult(recommendation, recommendationResult, completedContext, null);
    }

    private List<TodayWorkoutRenderBlock> buildBlocks(Long userId,
                                                      String assistantMessage,
                                                      WorkoutRecommendationService.ActionProposal actionProposal) {
        List<TodayWorkoutRenderBlock> blocks = new java.util.ArrayList<>(buildTextBlocks(assistantMessage));
        if (actionProposal != null) {
            blocks.add(new TodayWorkoutRenderBlock(
                    actionProposal.type(),
                    new TodayWorkoutCardBlockData(
                            actionProposal.cardType(),
                            actionProposal.title(),
                            actionProposal.impact(),
                            buildCardActions(userId, actionProposal)
                    )
            ));
        }
        return blocks;
    }

    private List<TodayWorkoutCardAction> buildCardActions(Long userId,
                                                          WorkoutRecommendationService.ActionProposal actionProposal) {
        if (actionProposal == null) {
            return List.of();
        }
        if (ACTION_UNDO_COMPLETE.equals(actionProposal.actionType())) {
            TodayWorkoutActionExecuteRequest requestBody = new TodayWorkoutActionExecuteRequest();
            requestBody.setActionType(ACTION_UNDO_COMPLETE);
            requestBody.setRecommendationId(actionProposal.recommendationId());
            requestBody.setRecordId(actionProposal.recordId());
            requestBody.setConfirmed(true);

            TodayWorkoutCardAction confirmAction = new TodayWorkoutCardAction(
                    actionProposal.confirmText(),
                    ACTION_UNDO_COMPLETE,
                    "danger",
                    new TodayWorkoutActionRequestMeta(
                            "POST",
                            ACTION_EXECUTE_PATH_TEMPLATE.formatted(userId),
                            requestBody
                    )
            );
            TodayWorkoutCardAction cancelAction = new TodayWorkoutCardAction(
                    "取消",
                    ACTION_CANCEL,
                    "secondary",
                    null
            );
            return List.of(confirmAction, cancelAction);
        }
        return List.of();
    }

    private List<TodayWorkoutRenderBlock> buildTextBlocks(String assistantMessage) {
        if (!StringUtils.hasText(assistantMessage)) {
            return List.of();
        }
        return List.of(new TodayWorkoutRenderBlock("text", new TodayWorkoutTextBlockData(assistantMessage)));
    }

    private List<TodayWorkoutRenderBlock> extractNonTextBlocks(List<TodayWorkoutRenderBlock> blocks) {
        if (CollectionUtils.isEmpty(blocks)) {
            return List.of();
        }
        return blocks.stream()
                .filter(block -> block != null && !"text".equals(block.getType()))
                .toList();
    }

    private WorkoutRecommendationService.ActionProposal attachActionContext(TodayWorkoutRecommendation recommendation,
                                                                            WorkoutRecommendationService.ActionProposal actionProposal,
                                                                            Long preloadedRecordId) {
        if (actionProposal == null || recommendation == null) {
            return null;
        }
        Long recordId = preloadedRecordId;
        if (recordId == null) {
            recordId = workoutRecordRepository
                    .findFirstByUserIdAndRecommendationIdAndWorkoutDateOrderByCreateTimeDesc(
                            recommendation.getUserId(),
                            recommendation.getId(),
                            recommendation.getRecommendationDate())
                    .map(WorkoutRecord::getId)
                    .orElse(null);
        }
        return new WorkoutRecommendationService.ActionProposal(
                actionProposal.actionType(),
                actionProposal.title(),
                actionProposal.impact(),
                actionProposal.confirmText(),
                recommendation.getId(),
                recordId,
                actionProposal.type(),
                actionProposal.cardType()
        );
    }

    private WorkoutRecommendationService.ActionProposal attachActionContext(TodayWorkoutRecommendation recommendation,
                                                                            WorkoutRecommendationService.ActionProposal actionProposal) {
        return attachActionContext(recommendation, actionProposal, null);
    }

    private WorkoutRecord resolveUndoTargetRecord(Long userId, TodayWorkoutActionExecuteRequest request, LocalDate today) {
        if (request.getRecordId() != null) {
            WorkoutRecord record = workoutRecordRepository.findByIdAndUserId(request.getRecordId(), userId).orElse(null);
            if (record != null && today.equals(record.getWorkoutDate())) {
                return record;
            }
            return null;
        }
        return workoutRecordRepository
                .findFirstByUserIdAndRecommendationIdAndWorkoutDateOrderByCreateTimeDesc(userId, request.getRecommendationId(), today)
                .orElse(null);
    }

    private TodayWorkoutActionExecuteResponse buildUndoExecuteResponse(TodayWorkoutRecommendation recommendation,
                                                                       WorkoutRecord record,
                                                                       boolean executed,
                                                                       boolean noOp,
                                                                       String message) {
        TodayWorkoutActionExecuteResponse response = new TodayWorkoutActionExecuteResponse();
        response.setActionType(ACTION_UNDO_COMPLETE);
        response.setExecuted(executed);
        response.setNoOp(noOp);
        response.setRecommendationId(recommendation.getId());
        response.setRecordId(record.getId());
        response.setMessage(message);
        response.setRecommendation(toRecommendationResponse(recommendation));
        response.setClearedBlocks(List.of());
        return response;
    }

    private void saveChatMessage(Long recommendationId, String role, String content) {
        saveChatMessage(recommendationId, role, content, null);
    }

    private void saveChatMessage(Long recommendationId, String role, String content, List<TodayWorkoutRenderBlock> renderBlocks) {
        todayWorkoutChatSessionService.appendMessage(recommendationId, role, content, renderBlocks);
    }

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
        if (completedWorkoutDayId.equals(recommendation.getBaseWorkoutDayId())) {
            return COMPLETION_MODE_BASE_PLAN;
        }
        if (completedWorkoutDayId.equals(recommendation.getRecommendedWorkoutDayId())) {
            return COMPLETION_MODE_RECOMMENDED;
        }
        return COMPLETION_MODE_CUSTOM;
    }

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
