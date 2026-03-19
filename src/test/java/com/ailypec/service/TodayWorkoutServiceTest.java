package com.ailypec.service;

import com.ailypec.dto.today.TodayStatusSubmitRequest;
import com.ailypec.dto.today.TodayWorkoutChatRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteResponse;
import com.ailypec.dto.today.TodayWorkoutRecommendationResponse;
import com.ailypec.entity.ProgressPointer;
import com.ailypec.entity.TodayStatus;
import com.ailypec.entity.TodayWorkoutChatMessage;
import com.ailypec.entity.TodayWorkoutRecommendation;
import com.ailypec.entity.WorkoutDay;
import com.ailypec.entity.WorkoutPlan;
import com.ailypec.entity.WorkoutRecord;
import com.ailypec.repository.ProgressPointerRepository;
import com.ailypec.repository.TodayStatusRepository;
import com.ailypec.repository.TodayWorkoutChatMessageRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
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
    private TodayWorkoutChatMessageRepository todayWorkoutChatMessageRepository;
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

    /**
     * 初始化测试所需的基础计划、指针和训练日数据。
     */
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

    /**
     * 核心测试：验证多轮对话中，针对伤病避让逻辑。
     * 场景：原本该练肩 -> 用户说肩部拉伤 -> AI 推荐练腿。
     */
    @Test
    void shouldRecommendLegsWhenShoulderIsStrainedInChat() {
        mockBaseContext();

        // 1. 模拟已有首轮推荐（原计划是练肩）
        TodayWorkoutRecommendation recommendation = new TodayWorkoutRecommendation();
        recommendation.setId(300L);
        recommendation.setUserId(1L);
        recommendation.setRecommendationDate(LocalDate.now());
        recommendation.setBaseWorkoutDayId(day1.getId());
        recommendation.setBaseContent(day1.getContent());
        recommendation.setRecommendedWorkoutDayId(day1.getId());
        recommendation.setRecommendationType("BASE_PLAN");
        recommendation.setCompleted(false);

        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(recommendation));

        // 2. 模拟用户发消息：“我肩膀疼，不能练肩，可以换成练腿吗？”
        TodayWorkoutChatRequest chatRequest = new TodayWorkoutChatRequest();
        chatRequest.setMessage("我肩膀疼，不能练肩，可以换成练腿吗？");

        // 模拟历史对话加载
        List<TodayWorkoutChatMessage> history = new ArrayList<>();
        history.add(new TodayWorkoutChatMessage()); // 用户的提问
        when(todayWorkoutChatMessageRepository.findByRecommendationIdOrderByCreateTimeAsc(300L)).thenReturn(history);

        // 3. 模拟 AI 返回结果（正确识别避让原则，推荐练腿）
        WorkoutRecommendationService.RecommendationResult legsResult = new WorkoutRecommendationService.RecommendationResult(
                day2.getId(), day2.getContent(), "ALTERNATIVE", "考虑到你肩膀受伤，我们避开上肢，今天改练腿部。", false
        );
        when(workoutRecommendationService.recommend(any(), any(), eq(day1), eq(List.of(day1, day2, day3)), eq(0)))
                .thenReturn(legsResult);

        // 4. 执行对话业务逻辑
        Result<TodayWorkoutRecommendationResponse> result = todayWorkoutService.chatTodayWorkout(1L, chatRequest);

        // 5. 断言结果
        assertTrue(result.isSuccess());
        assertEquals("ALTERNATIVE", result.getData().getRecommendationType());
        assertEquals(day2.getId(), result.getData().getRecommendedWorkoutDayId());
        assertEquals("练腿", result.getData().getRecommendedContent());

        // 验证快照是否已更新
        verify(todayWorkoutRecommendationRepository, atLeastOnce()).save(recommendation);
        assertEquals(day2.getId(), recommendation.getRecommendedWorkoutDayId());
        assertEquals("ALTERNATIVE", recommendation.getRecommendationType());
    }

    /**
     * 验证无状态描述时返回原计划训练日。
     */
    @Test
    void shouldReturnBaseDayWhenNoStatusDescription() {
        mockBaseContext();
        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(todayStatusRepository.findFirstByUserIdAndStatusDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(workoutRecommendationService.recommend(any(), any(), eq(day1), eq(List.of(day1, day2, day3)), eq(0)))
                .thenReturn(new WorkoutRecommendationService.RecommendationResult(day1.getId(), day1.getContent(), "BASE_PLAN", "今天按计划训练即可", false));
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

    /**
     * 验证同一天重复获取推荐时复用已有快照。
     */
    @Test
    void shouldReuseRecommendationSnapshotOnSameDay() {
        mockBaseContext();
        TodayWorkoutRecommendation existing = new TodayWorkoutRecommendation();
        existing.setId(301L);
        existing.setPlanId(activePlan.getId());
        existing.setBaseWorkoutDayId(day1.getId());
        existing.setBaseContent(day1.getContent());
        existing.setRecommendedWorkoutDayId(day1.getId());
        existing.setRecommendedContent(day1.getContent());
        existing.setRecommendationType("BASE_PLAN");
        existing.setRecommendationReason("沿用已有推荐");
        existing.setFallbackUsed(false);
        existing.setCompleted(false);
        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        Result<TodayWorkoutRecommendationResponse> result = todayWorkoutService.getTodayWorkout(1L);

        assertTrue(result.isSuccess());
        assertEquals(301L, result.getData().getRecommendationId());
        verify(workoutRecommendationService, never()).recommend(any(), any(), any(), any(), any(Integer.class));
    }

    /**
     * 验证完成替代推荐训练时会推进指针。
     */
    @Test
    void shouldAdvancePointerWhenRecommendedAlternativeCompleted() {
        mockBaseContext();
        TodayWorkoutRecommendation recommendation = new TodayWorkoutRecommendation();
        recommendation.setId(302L);
        recommendation.setRecommendationDate(LocalDate.now());
        recommendation.setBaseWorkoutDayId(day1.getId());
        recommendation.setBaseContent(day1.getContent());
        recommendation.setRecommendedWorkoutDayId(day2.getId());
        recommendation.setRecommendedContent(day2.getContent());
        recommendation.setRecommendationType("ALTERNATIVE");
        recommendation.setRecommendationReason("今天更适合练腿");
        recommendation.setCompleted(false);

        when(workoutRecordRepository.findByUserIdAndWorkoutDate(eq(1L), any(LocalDate.class)))
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
    }

    /**
     * 验证恢复建议完成后不会推进指针。
     */
    @Test
    void shouldNotAdvancePointerWhenRecoveryCompleted() {
        mockBaseContext();
        TodayWorkoutRecommendation recommendation = new TodayWorkoutRecommendation();
        recommendation.setId(303L);
        recommendation.setRecommendationDate(LocalDate.now());
        recommendation.setBaseWorkoutDayId(day1.getId());
        recommendation.setBaseContent(day1.getContent());
        recommendation.setRecommendationType("RECOVERY");
        recommendation.setRecommendedContent("今天做拉伸和散步");
        recommendation.setRecommendationReason("状态疲劳，优先恢复");
        recommendation.setCompleted(false);

        when(workoutRecordRepository.findByUserIdAndWorkoutDate(eq(1L), any(LocalDate.class)))
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

    /**
     * 验证同一天重复完成训练会被拒绝。
     */
    @Test
    void shouldRejectRepeatedCompleteOnSameDay() {
        mockBaseContext();
        WorkoutRecord existing = new WorkoutRecord();
        existing.setId(500L);
        when(workoutRecordRepository.findByUserIdAndWorkoutDate(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(existing));

        TodayWorkoutCompleteRequest request = new TodayWorkoutCompleteRequest();
        request.setRecommendationId(999L);

        Result<TodayWorkoutCompleteResponse> result = todayWorkoutService.completeTodayWorkout(1L, request);

        assertFalse(result.isSuccess());
        assertEquals("今天已经完成训练了，明天再来吧！", result.getMessage());
    }

    /**
     * 验证 AI 失败时回退到原计划训练。
     */
    @Test
    void shouldFallbackToBaseDayWhenAiFails() {
        mockBaseContext();
        TodayStatus status = new TodayStatus();
        status.setDescription("今天很累");
        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(todayStatusRepository.findFirstByUserIdAndStatusDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(status));
        when(workoutRecommendationService.recommend(any(), any(), eq(day1), eq(List.of(day1, day2, day3)), eq(0)))
                .thenReturn(new WorkoutRecommendationService.RecommendationResult(day1.getId(), day1.getContent(), "BASE_PLAN", "AI 推荐暂时不可用，已按计划返回今日训练", true));
        when(todayWorkoutRecommendationRepository.save(any(TodayWorkoutRecommendation.class)))
                .thenAnswer(invocation -> {
                    TodayWorkoutRecommendation rec = invocation.getArgument(0);
                    rec.setId(304L);
                    return rec;
                });

        Result<TodayWorkoutRecommendationResponse> result = todayWorkoutService.getTodayWorkout(1L);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().getFallbackUsed());
        assertEquals(day1.getContent(), result.getData().getRecommendedContent());
    }

    /**
     * 验证未显式传 recommendationId 时复用当天推荐快照完成训练。
     */
    @Test
    void shouldUseTodaySnapshotWhenCompleteWithoutRecommendationId() {
        mockBaseContext();
        TodayWorkoutRecommendation recommendation = new TodayWorkoutRecommendation();
        recommendation.setId(305L);
        recommendation.setRecommendationDate(LocalDate.now());
        recommendation.setBaseWorkoutDayId(day1.getId());
        recommendation.setBaseContent(day1.getContent());
        recommendation.setRecommendedWorkoutDayId(day1.getId());
        recommendation.setRecommendedContent(day1.getContent());
        recommendation.setRecommendationType("BASE_PLAN");
        recommendation.setRecommendationReason("按计划完成");
        recommendation.setCompleted(false);

        when(todayWorkoutRecommendationRepository.findFirstByUserIdAndRecommendationDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(recommendation));
        when(workoutRecordRepository.findByUserIdAndWorkoutDate(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of());
        when(todayWorkoutRecommendationRepository.findByIdAndUserId(305L, 1L)).thenReturn(Optional.of(recommendation));
        when(workoutRecordRepository.save(any(WorkoutRecord.class))).thenAnswer(invocation -> {
            WorkoutRecord record = invocation.getArgument(0);
            record.setId(403L);
            return record;
        });
        when(todayWorkoutRecommendationRepository.save(any(TodayWorkoutRecommendation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Result<TodayWorkoutCompleteResponse> result = todayWorkoutService.completeTodayWorkout(1L, null);

        assertTrue(result.isSuccess());
        assertEquals(305L, result.getData().getRecommendationId());
    }

    /**
     * 验证更新当天状态时会清理未完成的推荐快照。
     */
    @Test
    void shouldReplaceTodayStatusAndClearUncompletedSnapshot() {
        TodayStatus existing = new TodayStatus();
        existing.setId(900L);
        when(todayStatusRepository.findFirstByUserIdAndStatusDateOrderByCreateTimeDesc(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(todayStatusRepository.save(any(TodayStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(todayWorkoutRecommendationRepository.findByUserIdAndRecommendationDateAndCompletedFalse(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(new TodayWorkoutRecommendation()));

        TodayStatusSubmitRequest request = new TodayStatusSubmitRequest();
        request.setDescription("昨天没睡好，今天想轻一点");

        Result<String> result = todayWorkoutService.submitTodayStatus(1L, request);

        assertTrue(result.isSuccess());
        verify(todayWorkoutRecommendationRepository, atLeastOnce()).delete(any(TodayWorkoutRecommendation.class));
    }

    /**
     * 统一 mock today 主链路需要的基础上下文。
     */
    private void mockBaseContext() {
        when(workoutPlanRepository.findByUserIdAndIsActiveTrue(1L)).thenReturn(List.of(activePlan));
        when(progressPointerService.findByActivePlanId(activePlan.getId())).thenReturn(pointer);
        when(workoutDayRepository.findByWorkoutPlanIdOrderByDayOrderAsc(activePlan.getId())).thenReturn(List.of(day1, day2, day3));
    }
}
