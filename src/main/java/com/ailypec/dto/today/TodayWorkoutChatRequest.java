package com.ailypec.dto.today;

import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class TodayWorkoutChatRequest {
    /**
     * 用户输入的对话内容
     */
    private String message;

    /**
     * 兼容前端可能使用的字段名
     */
    private String description;

    public String getMessage() {
        if (StringUtils.hasText(message)) {
            return message;
        }
        return description;
    }
}
