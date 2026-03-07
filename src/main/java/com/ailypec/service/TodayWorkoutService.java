package com.ailypec.service;

import com.ailypec.entity.ProgressPointer;
import com.ailypec.entity.WorkoutDay;
import com.ailypec.entity.WorkoutPlan;
import com.ailypec.repository.ProgressPointerRepository;
import com.ailypec.repository.WorkoutDayRepository;
import com.ailypec.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TodayWorkoutService {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutDayRepository workoutDayRepository;
    private final ProgressPointerRepository progressPointerRepository;

    @Transactional(readOnly = true)
    public String getTodayWorkout(Long userId) {
        // 获取用户激活的计划
        WorkoutPlan activePlan = workoutPlanRepository.findByUserIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new RuntimeException("没有激活的训练计划"));

        // 获取进度指针
        ProgressPointer pointer = progressPointerRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("进度指针未初始化"));

        // 获取当前训练日
        List<WorkoutDay> days = workoutDayRepository.findByWorkoutPlanIdOrderByDayOrderAsc(activePlan.getId());
        if (days.isEmpty()) {
            throw new RuntimeException("训练计划为空");
        }

        int index = pointer.getCurrentDayIndex() % days.size();
        return days.get(index).getContent();
    }

    @Transactional
    public String completeTodayWorkout(Long userId) {
        ProgressPointer pointer = progressPointerRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("进度指针未初始化"));

        // 获取计划的总天数
        WorkoutPlan activePlan = workoutPlanRepository.findById(pointer.getActivePlanId())
                .orElseThrow(() -> new RuntimeException("激活的计划不存在"));
        List<WorkoutDay> days = workoutDayRepository.findByWorkoutPlanIdOrderByDayOrderAsc(activePlan.getId());

        if (days.isEmpty()) {
            throw new RuntimeException("训练计划为空");
        }

        // 推进指针
        int nextIndex = (pointer.getCurrentDayIndex() + 1) % days.size();
        pointer.setCurrentDayIndex(nextIndex);
        pointer.setLastWorkoutDate(LocalDateTime.now());
        progressPointerRepository.save(pointer);

        // 返回下次训练内容
        return days.get(nextIndex).getContent();
    }

    @Transactional
    public void initProgress(Long userId, Long planId) {
        // 检查是否已存在
        progressPointerRepository.findByUserId(userId).ifPresent(p -> {
            throw new RuntimeException("进度指针已存在");
        });

        ProgressPointer pointer = new ProgressPointer();
        pointer.setUserId(userId);
        pointer.setActivePlanId(planId);
        pointer.setCurrentDayIndex(0);
        progressPointerRepository.save(pointer);
    }

}
