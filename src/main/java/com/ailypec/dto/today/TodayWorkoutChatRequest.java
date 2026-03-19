package com.ailypec.dto.today;

import lombok.Data;

@Data
public class TodayWorkoutChatRequest {
    /**
     * 用户输入的对话内容
     */
    private String message;
}
