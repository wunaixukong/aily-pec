package com.ailypec.controller;

import com.ailypec.response.Result;
import com.ailypec.service.TodayWorkoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/today")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TodayWorkoutController {

    private final TodayWorkoutService todayWorkoutService;

    @GetMapping("/{userId}")
    public Result<String> getTodayWorkout(@PathVariable Long userId) {
        Result<String> workout = todayWorkoutService.getTodayWorkout(userId);
        log.info("Today's workout for user {} is {}", userId, workout);
        return workout;
    }

    @PostMapping("/{userId}/complete")
    public Result<String> completeTodayWorkout(@PathVariable Long userId) {
        return todayWorkoutService.completeTodayWorkout(userId);

    }


}
