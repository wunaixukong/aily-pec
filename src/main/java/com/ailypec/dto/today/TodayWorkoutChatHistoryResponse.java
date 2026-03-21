package com.ailypec.dto.today;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TodayWorkoutChatHistoryResponse {

    private Long recommendationId;
    private List<TodayWorkoutChatItem> messages = new ArrayList<>();
}
