package com.ailypec.service;

import com.ailypec.entity.ProgressPointer;
import com.ailypec.entity.WorkoutDay;
import com.ailypec.entity.WorkoutPlan;
import com.ailypec.repository.ProgressPointerRepository;
import com.ailypec.repository.WorkoutDayRepository;
import com.ailypec.repository.WorkoutPlanRepository;
import com.ailypec.response.Result;
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

    private final ProgressPointerRepository progressPointerRepository;

    private final ProgressPointerService progressPointerService;

    @Transactional
    public WorkoutPlan createPlan(WorkoutPlan plan) {
        // 设置关联关系
        for (WorkoutDay day : plan.getWorkoutDays()) {
            day.setWorkoutPlan(plan);
        }
        WorkoutPlan workoutPlan = workoutPlanRepository.save(plan);

        // 创建计划,同时需要创建一份新的指针
        this.progressPointerService.initProgressPointer(workoutPlan);
        return workoutPlan;
    }

    public List<WorkoutPlan> getUserPlans(Long userId) {
        return workoutPlanRepository.findByUserId(userId);
    }

    public Optional<WorkoutPlan> getActivePlan(Long userId) {
        return workoutPlanRepository.findByUserIdAndIsActiveTrue(userId).stream().findFirst();
    }

    public Result<Boolean> activatePlan(Long userId, Long planId) {
        // 先取消该用户的所有激活计划
        List<WorkoutPlan> activePlans = workoutPlanRepository.findByUserIdAndIsActiveTrue(userId);
        for (WorkoutPlan plan : activePlans) {
            plan.setIsActive(false);
        }
        workoutPlanRepository.saveAll(activePlans);
        // 激活指定计划
        Optional<WorkoutPlan> planOptional = workoutPlanRepository.findById(planId);
        if (planOptional.isEmpty()) {
            return Result.fail("计划不存在");
        }
        WorkoutPlan plan = planOptional.get();
        plan.setIsActive(true);
        workoutPlanRepository.save(plan);
        return Result.success(true);
    }

    public void deletePlan(Long planId) {
        workoutPlanRepository.deleteById(planId);
    }

    @Transactional
    public Result<WorkoutPlan> updatePlan(WorkoutPlan plan) {
        try {
            // 1. 查询原计划
            Optional<WorkoutPlan> planOptional = workoutPlanRepository.findById(plan.getId());
            if (planOptional.isEmpty()) {
                return Result.fail("计划不存在");
            }
            WorkoutPlan existingPlan = planOptional.get();

            // 2. 更新基本信息
            existingPlan.setName(plan.getName());

            // 3. 先删除所有训练日（简单粗暴方案）
            existingPlan.getWorkoutDays().clear();

            // 4. 添加新的训练日
            for (WorkoutDay day : plan.getWorkoutDays()) {
                day.setId(null); // 强制作为新增
                day.setWorkoutPlan(existingPlan);
                existingPlan.getWorkoutDays().add(day);
            }

            // 5. 保存
            WorkoutPlan edit = workoutPlanRepository.save(existingPlan);
            return Result.success(edit);
        } catch (Exception e) {
            return Result.fail("更新计划失败: " + e.getMessage());
        }
    }
}
