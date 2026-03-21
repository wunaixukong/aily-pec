package com.ailypec.service;

import com.ailypec.dto.today.TodayWorkoutActionExecuteRequest;
import com.ailypec.dto.today.TodayWorkoutActionExecuteResponse;
import com.ailypec.dto.today.TodayWorkoutActionRequestMeta;
import com.ailypec.dto.today.TodayWorkoutCardAction;
import com.ailypec.dto.today.TodayWorkoutCardBlockData;
import com.ailypec.dto.today.TodayStatusSubmitRequest;
import com.ailypec.dto.today.TodayWorkoutChatHistoryResponse;
import com.ailypec.dto.today.TodayWorkoutChatItem;
import com.ailypec.dto.today.TodayWorkoutChatRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteResponse;
import com.ailypec.dto.today.TodayWorkoutRecommendationResponse;
import com.ailypec.dto.today.TodayWorkoutRenderBlock;
import com.ailypec.dto.today.TodayWorkoutStreamDoneResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodayWorkoutServiceTest {

    @Mock
    private WorkoutPlanRepository workoutPlanRepository;
    @Mock
    private WorkoutDayRepository workoutDayRepository;
    @Mock
    private ProgressPointerRepository progressPointerRepository;
    @Mock
    private WorkoutRecordRepository workoutRecordRepository;
    @Mock
    private TodayStatusRepository todayStatusRepository;
    @Mock
    private TodayWorkoutRecommendationRepository todayWorkoutRecommendationRepository;
    @Mock
    private TodayWorkoutChatSessionService todayWorkoutChatSessionService;
    @Mock
    private WorkoutRecommendationService workoutRecommendationService;
    @Mock
    private ProgressPointerService progressPointerService;

    @InjectMocks
    private TodayWorkoutService todayWorkoutService;

    private WorkoutPlan activePlan;
    private ProgressPointer pointer;
    private WorkoutDay day1;
    private WorkoutDay day2;
    private WorkoutDay day3;

    @BeforeEach
    void setUp() {
        activePlan = new WorkoutPlan();
        activePlan.setId(100L);
        activePlan.setUserId(1L);

        pointer = new ProgressPointer();
        pointer.setId(200L);
        pointer.setUserId(1L);
        pointer.setActivePlanId(100L);
        pointer.setCurrentDayIndex(0);
        pointer.setLastWorkoutDate(LocalDateTime.of(2026, 3, 20, 8, 0));

        day1 = new WorkoutDay();
        day1.setId(11L);
        day1.setDayOrder(1);
        day1.setContent("肩部训练");

        day2 = new WorkoutDay();
        day2.setId(12L);
        day2.setDayOrder(2);
        day2.setContent("练腿");

        day3 = new WorkoutDay();
        day3.setId(13L);
        day3.setDayOrder(3);
        day3.setContent("核心训练");
    }

    @Test
    void shouldRecommendLegsWhenShoulderIsStrainedInChat() {
        mockBaseContext();

        TodayWorkoutRecommendation recommendation = recommendation(300L, false);
        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(recommendation));
        when(todayWorkoutChatSessionService.getMessages(300L))
                .thenReturn(List.of(new TodayWorkoutChatItem("user", "我肩膀疼，不能练肩", null)));

        TodayWorkoutChatRequest chatRequest = new TodayWorkoutChatRequest();
        chatRequest.setMessage("我肩膀疼，不能练肩，可以换成练腿吗？");

        WorkoutRecommendationService.RecommendationResult legsResult = new WorkoutRecommendationService.RecommendationResult(
                day2.getId(), day2.getContent(), "ALTERNATIVE", "考虑到你肩膀受伤，今天改练腿部。", false, null
        );
        when(workoutRecommendationService.recommend(any(), any(), eq(day1), eq(List.of(day1, day2, day3)), eq(0), eq(false)))
                .thenReturn(legsResult);

        Result<TodayWorkoutRecommendationResponse> result = todayWorkoutService.chatTodayWorkout(1L, chatRequest);

        assertTrue(result.isSuccess());
        assertEquals("ALTERNATIVE", result.getData().getRecommendationType());
        assertEquals(day2.getId(), result.getData().getRecommendedWorkoutDayId());
        assertEquals("练腿", result.getData().getRecommendedContent());
        verify(todayWorkoutRecommendationRepository, atLeastOnce()).save(recommendation);
        assertEquals(day2.getId(), recommendation.getRecommendedWorkoutDayId());
        assertEquals("ALTERNATIVE", recommendation.getRecommendationType());
        assertEquals(1, result.getData().getBlocks().size());
        assertEquals("text", result.getData().getBlocks().get(0).getType());
    }

    @Test
    void shouldReturnUndoCardBlockWhenCompletedAndUserWantsToUndo() {
        mockBaseContext();
        TodayWorkoutRecommendation recommendation = recommendation(311L, true);
        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(recommendation));
        when(todayWorkoutChatSessionService.getMessages(311L)).thenReturn(List.of());
        WorkoutRecord record = new WorkoutRecord();
        record.setId(701L);
        when(workoutRecordRepository.findFirstByUserIdAndRecommendationIdAndWorkoutDateOrderByCreateTimeDesc(eq(1L), eq(311L), any(LocalDate.class)))
                .thenReturn(Optional.of(record));

        WorkoutRecommendationService.ActionProposal proposal = new WorkoutRecommendationService.ActionProposal(
                "UNDO_COMPLETE", "撤回今天的打卡", "撤回后恢复为未完成", "确认撤回", null, null, "card", "ACTION_CONFIRM"
        );
        when(workoutRecommendationService.recommend(any(), any(), eq(day1), eq(List.of(day1, day2, day3)), eq(0), eq(true)))
                .thenReturn(new WorkoutRecommendationService.RecommendationResult(
                        day1.getId(), day1.getContent(), "BASE_PLAN", "可以帮你撤回今天的打卡", false, proposal
                ));

        TodayWorkoutChatRequest request = new TodayWorkoutChatRequest();
        request.setMessage("我误触打卡了，帮我撤回");

        Result<TodayWorkoutRecommendationResponse> result = todayWorkoutService.chatTodayWorkout(1L, request);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getData().getBlocks().size());
        assertEquals("text", result.getData().getBlocks().get(0).getType());
        assertEquals("card", result.getData().getBlocks().get(1).getType());
        TodayWorkoutCardBlockData card = (TodayWorkoutCardBlockData) result.getData().getBlocks().get(1).getData();
        assertEquals(2, card.getActions().size());
        TodayWorkoutCardAction confirmAction = card.getActions().get(0);
        assertEquals("UNDO_COMPLETE", confirmAction.getActionType());
        assertEquals("danger", confirmAction.getStyle());
        assertNotNull(confirmAction.getRequest());
        assertEquals("POST", confirmAction.getRequest().getMethod());
        assertEquals("/message/1/actions/execute", confirmAction.getRequest().getPath());
        TodayWorkoutActionExecuteRequest requestBody = (TodayWorkoutActionExecuteRequest) confirmAction.getRequest().getBody();
        assertEquals("UNDO_COMPLETE", requestBody.getActionType());
        assertEquals(311L, requestBody.getRecommendationId());
        assertEquals(701L, requestBody.getRecordId());
        assertTrue(Boolean.TRUE.equals(requestBody.getConfirmed()));
        TodayWorkoutCardAction cancelAction = card.getActions().get(1);
        assertEquals("CANCEL", cancelAction.getActionType());
        assertEquals("secondary", cancelAction.getStyle());
        assertNull(cancelAction.getRequest());
        verify(todayWorkoutChatSessionService).setPendingBlocks(eq(311L), any());
        verify(todayWorkoutRecommendationRepository, never()).save(recommendation);
    }

    @Test
    void shouldReturnGuidanceWhenCompletedButNotUndoIntent() {
        mockBaseContext();
        TodayWorkoutRecommendation recommendation = recommendation(312L, true);
        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(recommendation));
        when(todayWorkoutChatSessionService.getMessages(312L)).thenReturn(List.of());
        when(workoutRecommendationService.recommend(any(), any(), eq(day1), eq(List.of(day1, day2, day3)), eq(0), eq(true)))
                .thenReturn(new WorkoutRecommendationService.RecommendationResult(
                        day1.getId(), day1.getContent(), "BASE_PLAN", "今天已经打卡完成", false, null
                ));

        TodayWorkoutChatRequest request = new TodayWorkoutChatRequest();
        request.setMessage("今天练了什么来着");

        Result<TodayWorkoutRecommendationResponse> result = todayWorkoutService.chatTodayWorkout(1L, request);

        assertTrue(result.isSuccess());
        assertEquals("今日已打卡，如为误触可让我帮你撤回", result.getData().getRecommendationReason());
        assertEquals(1, result.getData().getBlocks().size());
        assertEquals("text", result.getData().getBlocks().get(0).getType());
    }

    @Test
    void shouldReturnBaseDayWhenNoStatusDescription() {
        mockBaseContext();
        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(todayStatusRepository.findFirstByUserIdAndStatusDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(workoutRecommendationService.recommend(any(), any(), eq(day1), eq(List.of(day1, day2, day3)), eq(0), eq(false)))
                .thenReturn(new WorkoutRecommendationService.RecommendationResult(day1.getId(), day1.getContent(), "BASE_PLAN", "今天按计划训练即可", false, null));
        when(todayWorkoutRecommendationRepository.save(any(TodayWorkoutRecommendation.class)))
                .thenAnswer(invocation -> {
                    TodayWorkoutRecommendation rec = invocation.getArgument(0);
                    rec.setId(300L);
                    return rec;
                });

        Result<TodayWorkoutRecommendationResponse> result = todayWorkoutService.getTodayWorkout(1L);

        assertTrue(result.isSuccess());
        assertEquals("BASE_PLAN", result.getData().getRecommendationType());
        assertEquals(day1.getId(), result.getData().getRecommendedWorkoutDayId());
        assertFalse(Boolean.TRUE.equals(result.getData().getFallbackUsed()));
    }

    @Test
    void shouldReuseRecommendationSnapshotOnSameDay() {
        mockBaseContext();
        TodayWorkoutRecommendation existing = recommendation(301L, false);
        existing.setPlanId(activePlan.getId());
        existing.setRecommendationReason("沿用已有推荐");
        existing.setFallbackUsed(false);
        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        Result<TodayWorkoutRecommendationResponse> result = todayWorkoutService.getTodayWorkout(1L);

        assertTrue(result.isSuccess());
        assertEquals(301L, result.getData().getRecommendationId());
        verify(workoutRecommendationService, never()).recommend(any(), any(), any(), any(), any(Integer.class), any(Boolean.class));
    }

    @Test
    void shouldAdvancePointerWhenRecommendedAlternativeCompleted() {
        mockBaseContext();
        TodayWorkoutRecommendation recommendation = recommendation(302L, false);
        recommendation.setRecommendedWorkoutDayId(day2.getId());
        recommendation.setRecommendedContent(day2.getContent());
        recommendation.setRecommendationType("ALTERNATIVE");
        recommendation.setRecommendationReason("今天更适合练腿");

        when(workoutRecordRepository.findByUserIdAndWorkoutDateAndRevokedFalse(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of());
        when(todayWorkoutRecommendationRepository.findByIdAndUserId(302L, 1L)).thenReturn(Optional.of(recommendation));
        when(workoutRecommendationService.findRecommendedDayById(List.of(day1, day2, day3), day2.getId())).thenReturn(Optional.of(day2));
        when(workoutRecordRepository.save(any(WorkoutRecord.class))).thenAnswer(invocation -> {
            WorkoutRecord record = invocation.getArgument(0);
            record.setId(401L);
            return record;
        });
        when(todayWorkoutRecommendationRepository.save(any(TodayWorkoutRecommendation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TodayWorkoutCompleteRequest request = new TodayWorkoutCompleteRequest();
        request.setRecommendationId(302L);
        request.setCompletionMode("RECOMMENDED");

        Result<TodayWorkoutCompleteResponse> result = todayWorkoutService.completeTodayWorkout(1L, request);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().getPointerAdvanced());
        assertEquals(1, result.getData().getNextDayIndex());
        verify(progressPointerRepository).save(pointer);

        ArgumentCaptor<WorkoutRecord> captor = ArgumentCaptor.forClass(WorkoutRecord.class);
        verify(workoutRecordRepository).save(captor.capture());
        assertEquals("RECOMMENDED", captor.getValue().getCompletionMode());
        assertEquals(day2.getId(), captor.getValue().getWorkoutDayId());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getPointerAdvanced()));
        assertEquals(0, captor.getValue().getPointerPreviousDayIndex());
        assertEquals(LocalDateTime.of(2026, 3, 20, 8, 0), captor.getValue().getPointerPreviousLastWorkoutDate());
    }

    @Test
    void shouldNotAdvancePointerWhenRecoveryCompleted() {
        mockBaseContext();
        TodayWorkoutRecommendation recommendation = recommendation(303L, false);
        recommendation.setRecommendationType("RECOVERY");
        recommendation.setRecommendedWorkoutDayId(null);
        recommendation.setRecommendedContent("今天做拉伸和散步");
        recommendation.setRecommendationReason("状态疲劳，优先恢复");

        when(workoutRecordRepository.findByUserIdAndWorkoutDateAndRevokedFalse(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of());
        when(todayWorkoutRecommendationRepository.findByIdAndUserId(303L, 1L)).thenReturn(Optional.of(recommendation));
        when(workoutRecordRepository.save(any(WorkoutRecord.class))).thenAnswer(invocation -> {
            WorkoutRecord record = invocation.getArgument(0);
            record.setId(402L);
            return record;
        });
        when(todayWorkoutRecommendationRepository.save(any(TodayWorkoutRecommendation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TodayWorkoutCompleteRequest request = new TodayWorkoutCompleteRequest();
        request.setRecommendationId(303L);

        Result<TodayWorkoutCompleteResponse> result = todayWorkoutService.completeTodayWorkout(1L, request);

        assertTrue(result.isSuccess());
        assertFalse(result.getData().getPointerAdvanced());
        verify(progressPointerRepository, never()).save(any(ProgressPointer.class));
    }

    @Test
    void shouldRejectRepeatedCompleteOnSameDay() {
        mockBaseContext();
        WorkoutRecord existing = new WorkoutRecord();
        existing.setId(500L);
        existing.setRevoked(false);
        when(workoutRecordRepository.findByUserIdAndWorkoutDateAndRevokedFalse(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(existing));

        TodayWorkoutCompleteRequest request = new TodayWorkoutCompleteRequest();
        request.setRecommendationId(999L);

        Result<TodayWorkoutCompleteResponse> result = todayWorkoutService.completeTodayWorkout(1L, request);

        assertFalse(result.isSuccess());
        assertEquals("今天已经完成训练了，明天再来吧！", result.getMessage());
    }

    @Test
    void shouldUndoCompletedWorkoutAndRestorePointer() {
        mockBaseContext();
        pointer.setCurrentDayIndex(1);
        pointer.setLastWorkoutDate(LocalDateTime.of(2026, 3, 21, 9, 0));
        TodayWorkoutRecommendation recommendation = recommendation(320L, true);
        WorkoutRecord record = new WorkoutRecord();
        record.setId(800L);
        record.setUserId(1L);
        record.setRecommendationId(320L);
        record.setWorkoutDate(LocalDate.now());
        record.setPointerAdvanced(true);
        record.setPointerPreviousDayIndex(0);
        record.setPointerPreviousLastWorkoutDate(LocalDateTime.of(2026, 3, 20, 8, 0));
        record.setRevoked(false);

        when(todayWorkoutRecommendationRepository.findByIdAndUserId(320L, 1L)).thenReturn(Optional.of(recommendation));
        when(workoutRecordRepository.findByIdAndUserId(800L, 1L)).thenReturn(Optional.of(record));
        when(todayWorkoutRecommendationRepository.save(any(TodayWorkoutRecommendation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutRecordRepository.save(any(WorkoutRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TodayWorkoutActionExecuteRequest request = new TodayWorkoutActionExecuteRequest();
        request.setRecommendationId(320L);
        request.setRecordId(800L);
        request.setActionType("UNDO_COMPLETE");
        request.setConfirmed(true);

        Result<TodayWorkoutActionExecuteResponse> result = todayWorkoutService.executeUndoComplete(1L, request);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().getExecuted());
        assertFalse(result.getData().getNoOp());
        assertFalse(recommendation.getCompleted());
        assertTrue(record.getRevoked());
        assertEquals(0, pointer.getCurrentDayIndex());
        assertEquals(LocalDateTime.of(2026, 3, 20, 8, 0), pointer.getLastWorkoutDate());
        verify(todayWorkoutChatSessionService).clearPendingBlocks(320L);
        verify(todayWorkoutChatSessionService).appendMessage(320L, "assistant", "已为你撤回今天的打卡");
        assertEquals(0, result.getData().getClearedBlocks().size());
    }

    @Test
    void shouldUndoWithoutRestoringPointerWhenPointerNotAdvanced() {
        mockBaseContext();
        TodayWorkoutRecommendation recommendation = recommendation(321L, true);
        WorkoutRecord record = new WorkoutRecord();
        record.setId(801L);
        record.setUserId(1L);
        record.setRecommendationId(321L);
        record.setWorkoutDate(LocalDate.now());
        record.setPointerAdvanced(false);
        record.setRevoked(false);

        when(todayWorkoutRecommendationRepository.findByIdAndUserId(321L, 1L)).thenReturn(Optional.of(recommendation));
        when(workoutRecordRepository.findByIdAndUserId(801L, 1L)).thenReturn(Optional.of(record));
        when(todayWorkoutRecommendationRepository.save(any(TodayWorkoutRecommendation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutRecordRepository.save(any(WorkoutRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TodayWorkoutActionExecuteRequest request = new TodayWorkoutActionExecuteRequest();
        request.setRecommendationId(321L);
        request.setRecordId(801L);
        request.setActionType("UNDO_COMPLETE");
        request.setConfirmed(true);

        Result<TodayWorkoutActionExecuteResponse> result = todayWorkoutService.executeUndoComplete(1L, request);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().getExecuted());
        verify(progressPointerRepository, never()).save(any(ProgressPointer.class));
    }

    @Test
    void shouldTreatRepeatedUndoAsNoOp() {
        TodayWorkoutRecommendation recommendation = recommendation(322L, false);
        WorkoutRecord record = new WorkoutRecord();
        record.setId(802L);
        record.setUserId(1L);
        record.setRecommendationId(322L);
        record.setWorkoutDate(LocalDate.now());
        record.setRevoked(true);

        when(todayWorkoutRecommendationRepository.findByIdAndUserId(322L, 1L)).thenReturn(Optional.of(recommendation));
        when(workoutRecordRepository.findByIdAndUserId(802L, 1L)).thenReturn(Optional.of(record));

        TodayWorkoutActionExecuteRequest request = new TodayWorkoutActionExecuteRequest();
        request.setRecommendationId(322L);
        request.setRecordId(802L);
        request.setActionType("UNDO_COMPLETE");
        request.setConfirmed(true);

        Result<TodayWorkoutActionExecuteResponse> result = todayWorkoutService.executeUndoComplete(1L, request);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().getNoOp());
        verify(progressPointerRepository, never()).save(any(ProgressPointer.class));
    }

    @Test
    void shouldRejectUndoWhenRecommendationAndRecordMismatch() {
        TodayWorkoutRecommendation recommendation = recommendation(323L, true);
        WorkoutRecord record = new WorkoutRecord();
        record.setId(803L);
        record.setUserId(1L);
        record.setRecommendationId(999L);
        record.setWorkoutDate(LocalDate.now());
        record.setRevoked(false);

        when(todayWorkoutRecommendationRepository.findByIdAndUserId(323L, 1L)).thenReturn(Optional.of(recommendation));
        when(workoutRecordRepository.findByIdAndUserId(803L, 1L)).thenReturn(Optional.of(record));

        TodayWorkoutActionExecuteRequest request = new TodayWorkoutActionExecuteRequest();
        request.setRecommendationId(323L);
        request.setRecordId(803L);
        request.setActionType("UNDO_COMPLETE");
        request.setConfirmed(true);

        Result<TodayWorkoutActionExecuteResponse> result = todayWorkoutService.executeUndoComplete(1L, request);

        assertFalse(result.isSuccess());
        assertEquals("recommendation 与 record 不匹配", result.getMessage());
    }

    @Test
    void shouldRejectUndoWhenRecordIsNotToday() {
        TodayWorkoutRecommendation recommendation = recommendation(324L, true);
        WorkoutRecord record = new WorkoutRecord();
        record.setId(804L);
        record.setUserId(1L);
        record.setRecommendationId(324L);
        record.setWorkoutDate(LocalDate.now().minusDays(1));
        record.setRevoked(false);

        when(todayWorkoutRecommendationRepository.findByIdAndUserId(324L, 1L)).thenReturn(Optional.of(recommendation));
        when(workoutRecordRepository.findByIdAndUserId(804L, 1L)).thenReturn(Optional.of(record));

        TodayWorkoutActionExecuteRequest request = new TodayWorkoutActionExecuteRequest();
        request.setRecommendationId(324L);
        request.setRecordId(804L);
        request.setActionType("UNDO_COMPLETE");
        request.setConfirmed(true);

        Result<TodayWorkoutActionExecuteResponse> result = todayWorkoutService.executeUndoComplete(1L, request);

        assertFalse(result.isSuccess());
        assertEquals("找不到可撤回的今日打卡记录", result.getMessage());
    }

    @Test
    void shouldReturnTodayChatHistoryFromCurrentRecommendation() {
        TodayWorkoutRecommendation recommendation = recommendation(307L, false);
        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(recommendation));

        TodayWorkoutChatHistoryResponse historyResponse = new TodayWorkoutChatHistoryResponse();
        historyResponse.setRecommendationId(307L);
        historyResponse.setMessages(List.of(new TodayWorkoutChatItem("assistant", "今天练腿", null)));
        historyResponse.setPendingBlocks(List.of(new TodayWorkoutRenderBlock(
                "card",
                new TodayWorkoutCardBlockData("ACTION_CONFIRM", "撤回今天的打卡", "撤回后恢复为未完成", List.of(
                        new TodayWorkoutCardAction(
                                "确认撤回",
                                "UNDO_COMPLETE",
                                "danger",
                                new TodayWorkoutActionRequestMeta(
                                        "POST",
                                        "/message/1/actions/execute",
                                        new TodayWorkoutActionExecuteRequest()
                                )
                        ),
                        new TodayWorkoutCardAction("取消", "CANCEL", "secondary", null)
                ))
        )));
        when(todayWorkoutChatSessionService.getHistory(307L)).thenReturn(historyResponse);

        Result<TodayWorkoutChatHistoryResponse> result = todayWorkoutService.getTodayWorkoutChatHistory(1L);

        assertTrue(result.isSuccess());
        assertEquals(307L, result.getData().getRecommendationId());
        assertEquals(1, result.getData().getMessages().size());
        assertEquals(1, result.getData().getPendingBlocks().size());
        assertEquals("card", result.getData().getPendingBlocks().get(0).getType());
    }

    @Test
    void shouldPersistAssistantAndUpdateSnapshotWhenFinalResultParsedSuccessfully() {
        TodayWorkoutRecommendation recommendation = recommendation(308L, false);
        when(todayWorkoutRecommendationRepository.findById(308L)).thenReturn(Optional.of(recommendation));
        when(todayWorkoutRecommendationRepository.save(any(TodayWorkoutRecommendation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkoutRecommendationService.RecommendationResult result = new WorkoutRecommendationService.RecommendationResult(
                day2.getId(), day2.getContent(), "ALTERNATIVE", "今天改练腿", false, null
        );
        when(workoutRecommendationService.parseResult("full-json", day1, List.of(day1, day2, day3))).thenReturn(result);

        TodayWorkoutStreamDoneResponse response = todayWorkoutService.updateRecommendationFromFinalResult(308L, "full-json", day1, List.of(day1, day2, day3), false);

        verify(todayWorkoutChatSessionService, times(1)).appendMessage(308L, "assistant", "今天改练腿");
        verify(todayWorkoutRecommendationRepository, times(1)).save(recommendation);
        verify(workoutRecommendationService, never()).extractVisibleRecommendationReason(any());
        assertEquals(day2.getId(), recommendation.getRecommendedWorkoutDayId());
        assertEquals(day2.getContent(), recommendation.getRecommendedContent());
        assertEquals("ALTERNATIVE", recommendation.getRecommendationType());
        assertEquals("今天改练腿", recommendation.getRecommendationReason());
        assertEquals("今天改练腿", response.getAssistantMessage());
        assertEquals(1, response.getBlocks().size());
        assertEquals("text", response.getBlocks().get(0).getType());
    }

    @Test
    void shouldReturnDoneWithCardBlockForCompletedStreamChat() {
        TodayWorkoutRecommendation recommendation = recommendation(309L, true);
        when(todayWorkoutRecommendationRepository.findById(309L)).thenReturn(Optional.of(recommendation));
        WorkoutRecord record = new WorkoutRecord();
        record.setId(901L);
        when(workoutRecordRepository.findFirstByUserIdAndRecommendationIdAndWorkoutDateOrderByCreateTimeDesc(eq(1L), eq(309L), any(LocalDate.class)))
                .thenReturn(Optional.of(record));
        WorkoutRecommendationService.ActionProposal proposal = new WorkoutRecommendationService.ActionProposal(
                "UNDO_COMPLETE", "撤回今天的打卡", "撤回后恢复为未完成", "确认撤回", null, null, "card", "ACTION_CONFIRM"
        );
        when(workoutRecommendationService.parseResult("done-json", day1, List.of(day1, day2, day3)))
                .thenReturn(new WorkoutRecommendationService.RecommendationResult(
                        day1.getId(), day1.getContent(), "BASE_PLAN", "可以帮你撤回今天的打卡", false, proposal
                ));

        TodayWorkoutStreamDoneResponse response = todayWorkoutService.updateRecommendationFromFinalResult(309L, "done-json", day1, List.of(day1, day2, day3), true);

        assertEquals(2, response.getBlocks().size());
        assertEquals("card", response.getBlocks().get(1).getType());
        verify(todayWorkoutChatSessionService).setPendingBlocks(eq(309L), any());
    }

    @Test
    void shouldPersistAssistantOnlyWhenFinalResultParseFailsButVisibleReasonExists() {
        TodayWorkoutRecommendation recommendation = recommendation(310L, false);
        recommendation.setRecommendationType("BASE_PLAN");
        recommendation.setRecommendationReason("原推荐");
        when(todayWorkoutRecommendationRepository.findById(310L)).thenReturn(Optional.of(recommendation));
        when(workoutRecommendationService.parseResult("broken-json", day1, List.of(day1, day2, day3))).thenReturn(null);
        when(workoutRecommendationService.extractVisibleRecommendationReason("broken-json")).thenReturn("前端已看到的解释");

        TodayWorkoutStreamDoneResponse response = todayWorkoutService.updateRecommendationFromFinalResult(310L, "broken-json", day1, List.of(day1, day2, day3), false);

        verify(todayWorkoutChatSessionService, times(1)).appendMessage(310L, "assistant", "前端已看到的解释");
        verify(todayWorkoutRecommendationRepository, never()).save(any(TodayWorkoutRecommendation.class));
        assertEquals("前端已看到的解释", response.getAssistantMessage());
        assertEquals(1, response.getBlocks().size());
    }

    @Test
    void shouldReplaceTodayStatusAndClearUncompletedSnapshot() {
        TodayStatus existing = new TodayStatus();
        existing.setId(900L);
        when(todayStatusRepository.findFirstByUserIdAndStatusDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(todayStatusRepository.save(any(TodayStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TodayWorkoutRecommendation recommendation = new TodayWorkoutRecommendation();
        recommendation.setId(306L);
        when(todayWorkoutRecommendationRepository.findByUserIdAndRecommendationDateAndCompletedFalse(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(recommendation));

        TodayStatusSubmitRequest request = new TodayStatusSubmitRequest();
        request.setDescription("昨天没睡好，今天想轻一点");

        Result<String> result = todayWorkoutService.submitTodayStatus(1L, request);

        assertTrue(result.isSuccess());
        verify(todayWorkoutChatSessionService).deleteSession(306L);
        verify(todayWorkoutRecommendationRepository, atLeastOnce()).delete(any(TodayWorkoutRecommendation.class));
    }

    private TodayWorkoutRecommendation recommendation(Long id, boolean completed) {
        TodayWorkoutRecommendation recommendation = new TodayWorkoutRecommendation();
        recommendation.setId(id);
        recommendation.setUserId(1L);
        recommendation.setPlanId(activePlan.getId());
        recommendation.setRecommendationDate(LocalDate.now());
        recommendation.setBaseWorkoutDayId(day1.getId());
        recommendation.setBaseContent(day1.getContent());
        recommendation.setRecommendedWorkoutDayId(day1.getId());
        recommendation.setRecommendedContent(day1.getContent());
        recommendation.setRecommendationType("BASE_PLAN");
        recommendation.setRecommendationReason("按计划完成");
        recommendation.setCompleted(completed);
        recommendation.setFallbackUsed(false);
        return recommendation;
    }

    private void mockBaseContext() {
        when(workoutPlanRepository.findByUserIdAndIsActiveTrue(1L)).thenReturn(List.of(activePlan));
        when(progressPointerService.findByActivePlanId(activePlan.getId())).thenReturn(pointer);
        when(workoutDayRepository.findByWorkoutPlanIdOrderByDayOrderAsc(activePlan.getId())).thenReturn(List.of(day1, day2, day3));
    }
}
