package com.ailypec.controller;

import com.ailypec.entity.WorkoutPlan;
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
    public ResponseEntity<Void> activatePlan(@PathVariable Long planId, @RequestParam Long userId) {
        workoutPlanService.activatePlan(userId, planId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long planId) {
        workoutPlanService.deletePlan(planId);
        return ResponseEntity.ok().build();
    }

}
