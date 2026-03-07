package com.ailypec.controller;

import com.ailypec.entity.WorkoutPlan;
import com.ailypec.response.Result;
import com.ailypec.service.WorkoutPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkoutPlanController {

    private final WorkoutPlanService workoutPlanService;

    @PostMapping
    public ResponseEntity<WorkoutPlan> createPlan(@RequestBody WorkoutPlan plan) {
        return ResponseEntity.ok(workoutPlanService.createPlan(plan));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<WorkoutPlan>> getUserPlans(@PathVariable Long userId) {
        return ResponseEntity.ok(workoutPlanService.getUserPlans(userId));
    }

    @GetMapping("/active/{userId}")
    public ResponseEntity<WorkoutPlan> getActivePlan(@PathVariable Long userId) {
        return workoutPlanService.getActivePlan(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{planId}/activate")
    public Result<Boolean> activatePlan(@PathVariable Long planId, @RequestParam Long userId) {
        return workoutPlanService.activatePlan(userId, planId);
    }

    @PostMapping("/edit")
    public Result<WorkoutPlan> editPlan(@RequestBody WorkoutPlan plan) {
        return workoutPlanService.updatePlan(plan);
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long planId) {
        workoutPlanService.deletePlan(planId);
        return ResponseEntity.ok().build();
    }

}
