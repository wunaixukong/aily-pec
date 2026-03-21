package com.ailypec.service;

import com.ailypec.config.AiWorkoutProperties;
import com.ailypec.entity.WorkoutDay;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiWorkoutRecommendationServiceTest {

    @Test
    void shouldExtractReasonFromSingleTokenWithoutTrailingJson() {
        AiWorkoutRecommendationService.RecommendationReasonStreamParser parser =
                new AiWorkoutRecommendationService.RecommendationReasonStreamParser();
        List<String> emitted = new ArrayList<>();

        boolean delivered = parser.pushToken(
                "{\"recommendationReason\":\"train as planned\",\"other\":\"ignored\"}",
                emitted::add
        );

        assertTrue(delivered);
        assertEquals("train as planned", String.join("", emitted));
    }

    @Test
    void shouldExtractReasonAcrossChunksAndDecodeEscapes() {
        AiWorkoutRecommendationService.RecommendationReasonStreamParser parser =
                new AiWorkoutRecommendationService.RecommendationReasonStreamParser();
        List<String> emitted = new ArrayList<>();

        parser.pushToken("{\"recommendationReason\"", emitted::add);
        parser.pushToken(" : \"shoulder\\", emitted::add);
        parser.pushToken("\" tight\\\",\\ntrain legs\\u0021\"", emitted::add);
        parser.pushToken(",\"other\":\"ignored\"}", emitted::add);

        assertEquals("shoulder\" tight\",\ntrain legs!", String.join("", emitted));
    }

    @Test
    void shouldParseRecommendationResultFromWrappedJson() {
        AiWorkoutRecommendationService service = createService();
        WorkoutDay baseDay = workoutDay(11L, "push");
        WorkoutDay alternativeDay = workoutDay(12L, "legs");

        WorkoutRecommendationService.RecommendationResult result = service.parseResult("""
                ```json
                {
                  "recommendationType": "ALTERNATIVE",
                  "recommendedWorkoutDayId": 12,
                  "recommendedContent": "legs",
                  "recommendationReason": "switch to legs today"
                }
                ```
                """, baseDay, List.of(baseDay, alternativeDay));

        assertNotNull(result);
        assertEquals(12L, result.recommendedWorkoutDayId());
        assertEquals("legs", result.recommendedContent());
        assertEquals("ALTERNATIVE", result.recommendationType());
        assertEquals("switch to legs today", result.recommendationReason());
    }

    @Test
    void shouldParseRecoveryResultWithNullDayId() {
        AiWorkoutRecommendationService service = createService();
        WorkoutDay baseDay = workoutDay(11L, "push");

        WorkoutRecommendationService.RecommendationResult result = service.parseResult("""
                {
                  "recommendationType": "RECOVERY",
                  "recommendedWorkoutDayId": null,
                  "recommendedContent": "rest and mobility",
                  "recommendationReason": "recovery is better today"
                }
                """, baseDay, List.of(baseDay));

        assertNotNull(result);
        assertNull(result.recommendedWorkoutDayId());
        assertEquals("rest and mobility", result.recommendedContent());
        assertEquals("RECOVERY", result.recommendationType());
        assertEquals("recovery is better today", result.recommendationReason());
    }

    @Test
    void shouldExtractReasonFromCompleteJsonPayload() {
        AiWorkoutRecommendationService service = createService();

        String reason = service.extractVisibleRecommendationReason("""
                {
                  "recommendationType": "BASE_PLAN",
                  "recommendedWorkoutDayId": 11,
                  "recommendedContent": "push",
                  "recommendationReason": "按计划完成今天的推训练"
                }
                """);

        assertEquals("按计划完成今天的推训练", reason);
    }

    @Test
    void shouldExtractReasonFromIncompleteStreamPayload() {
        AiWorkoutRecommendationService service = createService();

        String reason = service.extractVisibleRecommendationReason("{" +
                "\"recommendationReason\":\"肩膀不适，今天先练腿\"" +
                ",\"recommendedContent\":");

        assertEquals("肩膀不适，今天先练腿", reason);
    }

    @Test
    void shouldReturnNullWhenVisibleReasonCannotBeExtracted() {
        AiWorkoutRecommendationService service = createService();

        String reason = service.extractVisibleRecommendationReason("{\"recommendedContent\":\"legs\"}");

        assertNull(reason);
    }

    private AiWorkoutRecommendationService createService() {
        return new AiWorkoutRecommendationService(
                new RestTemplateBuilder(),
                new AiWorkoutProperties()
        );
    }

    private WorkoutDay workoutDay(Long id, String content) {
        WorkoutDay day = new WorkoutDay();
        day.setId(id);
        day.setContent(content);
        return day;
    }
}
