package com.ailypec.dto.today;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodayWorkoutChatItem {

    private String role;
    private String content;
    private java.util.List<TodayWorkoutRenderBlock> renderBlocks;
    private java.time.LocalDateTime createTime;
}
