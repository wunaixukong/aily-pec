package com.ailypec.controller;

import com.ailypec.service.TodayWorkoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/today")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TodayWorkoutController {

    private final TodayWorkoutService todayWorkoutService;

    @GetMapping("/{userId}")
    public ResponseEntity<String> getTodayWorkout(@PathVariable Long userId) {
        return ResponseEntity.ok(todayWorkoutService.getTodayWorkout(userId));
    }

    @PostMapping("/{userId}/complete")
    public ResponseEntity<String> completeTodayWorkout(@PathVariable Long userId) {
        String nextWorkout = todayWorkoutService.completeTodayWorkout(userId);
        return ResponseEntity.ok(nextWorkout);
    }

    @PostMapping("/{userId}/init")
    public ResponseEntity<Void> initProgress(@PathVariable Long userId, @RequestBody Map<String, Long> request) {
        Long planId = request.get("planId");
        todayWorkoutService.initProgress(userId, planId);
        return ResponseEntity.ok().build();
    }

}
