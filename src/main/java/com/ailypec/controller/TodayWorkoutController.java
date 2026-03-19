package com.ailypec.controller;

import com.ailypec.dto.today.TodayStatusSubmitRequest;
import com.ailypec.dto.today.TodayWorkoutChatRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteResponse;
import com.ailypec.dto.today.TodayWorkoutRecommendationResponse;
import com.ailypec.response.Result;
import com.ailypec.service.TodayWorkoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/today")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TodayWorkoutController {

    private final TodayWorkoutService todayWorkoutService;

    /**
     * 提交用户当天的状态描述。
     */
    @PostMapping("/{userId}/status")
    public Result<String> submitTodayStatus(@PathVariable Long userId, @RequestBody TodayStatusSubmitRequest request) {
        return todayWorkoutService.submitTodayStatus(userId, request);
    }

    /**
     * 与 AI 进一步对话，调整今日推荐。
     */
    @PostMapping("/{userId}/chat")
    public Result<TodayWorkoutRecommendationResponse> chatTodayWorkout(@PathVariable Long userId, @RequestBody TodayWorkoutChatRequest request) {
        return todayWorkoutService.chatTodayWorkout(userId, request);
    }

    /**
     * 获取用户当天的训练推荐结果。
     */
    @GetMapping("/{userId}")
    public Result<TodayWorkoutRecommendationResponse> getTodayWorkout(@PathVariable Long userId) {
        Result<TodayWorkoutRecommendationResponse> workout = todayWorkoutService.getTodayWorkout(userId);
        log.info("Today's workout for user {} is {}", userId, workout);
        return workout;
    }

    /**
     * 提交当天训练完成结果。
     */
    @PostMapping("/{userId}/complete")
    public Result<TodayWorkoutCompleteResponse> completeTodayWorkout(@PathVariable Long userId,
                                                                     @RequestBody(required = false) TodayWorkoutCompleteRequest request) {
        return todayWorkoutService.completeTodayWorkout(userId, request);
    }
}
