package com.ailypec.service;

import com.ailypec.entity.ProgressPointer;
import com.ailypec.entity.WorkoutDay;
import com.ailypec.entity.WorkoutPlan;
import com.ailypec.repository.ProgressPointerRepository;
import com.ailypec.repository.UserRepository;
import com.ailypec.repository.WorkoutDayRepository;
import com.ailypec.repository.WorkoutPlanRepository;
import com.ailypec.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TodayWorkoutService {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutDayRepository workoutDayRepository;
    private final ProgressPointerRepository progressPointerRepository;

    private final WorkoutPlanService workoutPlanService;

    private final ProgressPointerService progressPointerService;

    public Result<String> getTodayWorkout(Long userId) {
        Result<String> result = Result.create();
        // 获取用户激活的计划
        List<WorkoutPlan> plans = workoutPlanRepository.findByUserIdAndIsActiveTrue(userId);
        if (CollectionUtils.isEmpty(plans)) {
            result.setMessage("没有激活的训练计划");
            return result;
        }
        WorkoutPlan activePlan = plans.get(0);
        // 获取进度指针
        ProgressPointer pointer = progressPointerService.findByActivePlanId(activePlan.getId());
        if (pointer == null){
            result.setMessage("进度指针未初始化");
            return result;
        }

        // 获取当前训练日
        List<WorkoutDay> days = workoutDayRepository.findByWorkoutPlanIdOrderByDayOrderAsc(activePlan.getId());
        if (days.isEmpty()) {
            result.setMessage("进度指针未初始化");
            return result;
        }

        int index = pointer.getCurrentDayIndex() % days.size();
        return Result.success(days.get(index).getContent());
    }

    @Transactional
    public Result<String> completeTodayWorkout(Long userId) {
        // 1. 先查询当前的计划
        List<WorkoutPlan> activePlans =  workoutPlanRepository.findByUserIdAndIsActiveTrue(userId);
        if (CollectionUtils.isEmpty(activePlans)) {
            return Result.fail("当前用户没有计划,请先创建一个计划");
        }
        WorkoutPlan activePlan = activePlans.stream().sorted(Comparator.comparing(WorkoutPlan::getCreateTime))
                .findFirst().get();


        // 2. 根据当前计划id查询,再去查询进度指针
        ProgressPointer pointer = progressPointerRepository.findByActivePlanId(activePlan.getId());
        if (pointer == null){
            // 有计划但是没有进度的时候,创建兜底进度
            progressPointerService.initProgressPointer(activePlan);
            // 再查一次
            pointer = progressPointerRepository.findByActivePlanId(activePlan.getId());
            if (pointer == null){
                return Result.fail("初始化进度失败");
            }
        }

        List<WorkoutDay> days = workoutDayRepository.findByWorkoutPlanIdOrderByDayOrderAsc(activePlan.getId());

        if (days.isEmpty()) {
            return Result.fail("训练计划为空");
        }

        // 推进指针
        int nextIndex = (pointer.getCurrentDayIndex() + 1) % days.size();
        pointer.setCurrentDayIndex(nextIndex);
        pointer.setLastWorkoutDate(LocalDateTime.now());
        progressPointerRepository.save(pointer);

        // 返回下次训练内容
        return Result.success(days.get(nextIndex).getContent());
    }


    @Transactional
    public Result<Boolean> initProgress(Long userId, Long planId) {
        Result<Boolean> result = Result.create();
        // 检查是否已存在
        if (progressPointerRepository.findByUserId(userId).isEmpty()) {
            ProgressPointer pointer = new ProgressPointer();
            pointer.setUserId(userId);
            pointer.setActivePlanId(planId);
            pointer.setCurrentDayIndex(0);
            progressPointerRepository.save(pointer);
            result.setMessage("进度指针初始化成功");
        }else {
            result.setMessage("进度指针已存在");
        }
        return result;
    }

}
