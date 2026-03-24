package com.ailypec.service;

import com.ailypec.dto.today.TodayWorkoutActionExecuteRequest;
import com.ailypec.dto.today.TodayWorkoutActionExecuteResponse;
import com.ailypec.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TodayWorkoutActionService {

    private static final String ACTION_UNDO_COMPLETE = "UNDO_COMPLETE";

    private final TodayWorkoutService todayWorkoutService;

    public Result<TodayWorkoutActionExecuteResponse> execute(Long userId, TodayWorkoutActionExecuteRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.getConfirmed())) {
            return Result.fail("请先确认执行动作");
        }
        if (!StringUtils.hasText(request.getActionType())) {
            return Result.fail("actionType 不能为空");
        }
        String actionType = request.getActionType().trim().toUpperCase();
        if (!ACTION_UNDO_COMPLETE.equals(actionType)) {
            return Result.fail("不支持的动作类型");
        }
        return todayWorkoutService.executeUndoComplete(userId, request);
    }
}
