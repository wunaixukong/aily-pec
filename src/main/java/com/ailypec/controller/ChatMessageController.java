package com.ailypec.controller;

import com.ailypec.dto.today.TodayWorkoutActionExecuteRequest;
import com.ailypec.dto.today.TodayWorkoutActionExecuteResponse;
import com.ailypec.dto.today.TodayWorkoutChatHistoryResponse;
import com.ailypec.dto.today.TodayWorkoutChatRequest;
import com.ailypec.dto.today.TodayWorkoutRecommendationResponse;
import com.ailypec.response.Result;
import com.ailypec.service.TodayWorkoutActionService;
import com.ailypec.service.TodayWorkoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatMessageController {

    private final TodayWorkoutService todayWorkoutService;
    private final TodayWorkoutActionService todayWorkoutActionService;

    /**
     * 与 AI 进一步对话，调整今日推荐。
     */
    @PostMapping("/{userId}/chat")
    public Result<TodayWorkoutRecommendationResponse> chatTodayWorkout(@PathVariable Long userId,
                                                                       @RequestBody TodayWorkoutChatRequest request) {
        return todayWorkoutService.chatTodayWorkout(userId, request);
    }

    /**
     * 流式推送与 AI 对话内容。
     */
    @PostMapping("/{userId}/chat/stream")
    public SseEmitter chatTodayWorkoutStream(@PathVariable Long userId,
                                             @RequestBody TodayWorkoutChatRequest request) {
        return todayWorkoutService.chatTodayWorkoutStream(userId, request);
    }

    /**
     * 获取用户当天的训练推荐结果。
     */
    @GetMapping("/{userId}/history")
    public Result<TodayWorkoutChatHistoryResponse> getTodayWorkoutChatHistory(@PathVariable Long userId) {
        return todayWorkoutService.getTodayWorkoutChatHistory(userId);
    }

    @PostMapping("/{userId}/actions/execute")
    public Result<TodayWorkoutActionExecuteResponse> executeAction(@PathVariable Long userId,
                                                                   @RequestBody TodayWorkoutActionExecuteRequest request) {
        return todayWorkoutActionService.execute(userId, request);
    }
}
