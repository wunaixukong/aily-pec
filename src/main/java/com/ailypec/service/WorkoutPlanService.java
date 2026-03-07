package com.ailypec.service;

import com.ailypec.entity.WorkoutDay;
import com.ailypec.entity.WorkoutPlan;
import com.ailypec.repository.WorkoutDayRepository;
import com.ailypec.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkoutPlanService {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutDayRepository workoutDayRepository;

    @Transactional
    public WorkoutPlan createPlan(WorkoutPlan plan) {
        // 设置关联关系
        for (WorkoutDay day : plan.getWorkoutDays()) {
            day.setWorkoutPlan(plan);
        }
        return workoutPlanRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public List<WorkoutPlan> getUserPlans(Long userId) {
        return workoutPlanRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<WorkoutPlan> getActivePlan(Long userId) {
        return workoutPlanRepository.findByUserIdAndIsActiveTrue(userId);
    }

    @Transactional
    public void activatePlan(Long userId, Long planId) {
        // 先取消该用户的所有激活计划
        List<WorkoutPlan> userPlans = workoutPlanRepository.findByUserId(userId);
        for (WorkoutPlan plan : userPlans) {
            plan.setIsActive(false);
        }
        workoutPlanRepository.saveAll(userPlans);

        // 激活指定计划
        WorkoutPlan plan = workoutPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("计划不存在"));
        plan.setIsActive(true);
        workoutPlanRepository.save(plan);
    }

    @Transactional
    public void deletePlan(Long planId) {
        workoutPlanRepository.deleteById(planId);
    }

}
