package com.ailypec.controller;

import com.ailypec.dto.today.TodayStatusSubmitRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteRequest;
import com.ailypec.dto.today.TodayWorkoutCompleteResponse;
import com.ailypec.dto.today.TodayWorkoutNextResponse;
import com.ailypec.dto.today.TodayWorkoutRecommendationResponse;
import com.ailypec.response.Result;
import com.ailypec.service.TodayWorkoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/today")
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


    @GetMapping("/{userId}")
    public Result<TodayWorkoutRecommendationResponse> getTodayWorkout(@PathVariable Long userId) {
        Result<TodayWorkoutRecommendationResponse> workout = todayWorkoutService.getTodayWorkout(userId);
        log.info("Today's workout for user {} is {}", userId, workout);
        return workout;
    }

    @GetMapping("/{userId}/next")
    public Result<TodayWorkoutNextResponse> getNextWorkout(@PathVariable Long userId) {
        return todayWorkoutService.getNextWorkout(userId);
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
