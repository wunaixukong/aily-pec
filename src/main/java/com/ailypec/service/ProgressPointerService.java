package com.ailypec.service;

import com.ailypec.entity.ProgressPointer;
import com.ailypec.entity.WorkoutPlan;
import com.ailypec.repository.ProgressPointerRepository;
import com.ailypec.repository.WorkoutPlanRepository;
import com.ailypec.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProgressPointerService {

    private final ProgressPointerRepository progressPointerRepository;

    private final WorkoutPlanRepository workoutPlanRepository;

    public ProgressPointer findByActivePlanId(Long planId) {
        ProgressPointer pointer = progressPointerRepository.findByActivePlanId(planId);
        if (pointer == null){
            workoutPlanRepository.findById(planId).ifPresent(workoutPlan -> {
                initProgressPointer(workoutPlan);
            });
            // 再查一次
            pointer = progressPointerRepository.findByActivePlanId(planId);

        }
        return pointer;
    }

    public void initProgressPointer(WorkoutPlan plan) {
        ProgressPointer pointer = builderProgressPointer(plan);
        progressPointerRepository.save(pointer);
    }

    private ProgressPointer builderProgressPointer(WorkoutPlan plan) {
        ProgressPointer pointer = new ProgressPointer();
        pointer.setActivePlanId(plan.getId());
        pointer.setCurrentDayIndex(0);
        pointer.setUserId(plan.getUserId());
        return pointer;
    }
}
