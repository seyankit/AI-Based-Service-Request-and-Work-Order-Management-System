package ph.edu.htcgsc.serviceportal.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiRecommendationServiceTest {

    @Test
    void completedAnalysisUpdatesExistingRecommendationWithoutWorkflowMutation() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        AiRecommendationService service = new AiRecommendationService(repository,
                new AiServiceClient(config(), (uri, token, body) -> new AiServiceClient.RawResponse(200, validResponse())));
        AiRecommendationService.Result result = service.analyze(17, 2, 7);
        assertEquals("Completed", result.analysisStatus());
        assertEquals(1, repository.completed);
        assertEquals(0, repository.failed);
    }

    @Test
    void unavailableServiceRecordsUnavailableStateAndDoesNotThrow() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        AiRecommendationService service = new AiRecommendationService(repository,
                new AiServiceClient(config(), (uri, token, body) -> { throw new IOException("offline"); }));
        AiRecommendationService.Result result = service.analyze(17, 2, 7);
        assertEquals(false, result.available());
        assertEquals("Unavailable", repository.failureStatus);
    }

    private AiServiceClient.Configuration config() {
        return new AiServiceClient.Configuration("http://127.0.0.1:8091", "a-very-long-test-token-that-is-never-logged");
    }

    private String validResponse() {
        return "{\"success\":true,\"data\":{\"analysisStatus\":\"Completed\",\"recommendationMethod\":\"Rule-Based\","
                + "\"recommendedCategoryId\":1,\"categoryConfidence\":0.5,\"recommendedPriority\":\"Medium\",\"priorityConfidence\":0.5,"
                + "\"possibleDuplicateRequestId\":null,\"duplicateSimilarity\":null,\"duplicateThreshold\":0.58,\"duplicateCandidates\":[],"
                + "\"categoryExplanation\":\"Category reason.\",\"priorityExplanation\":\"Priority reason.\",\"duplicateExplanation\":\"No duplicate.\","
                + "\"analysisMessage\":\"Advisory only.\",\"modelName\":\"Rules\",\"modelVersion\":\"1\",\"generatedAt\":\"2026-09-21T00:00:00+00:00\",\"processingTimeMs\":1}}";
    }

    private static final class RecordingRepository implements AiRecommendationService.Repository {
        int completed;
        int failed;
        String failureStatus;

        @Override public AiServiceClient.AnalysisInput loadInput(int actor, int role, long requestId) {
            return new AiServiceClient.AnalysisInput(7, "Network outage", "Internet unavailable.", "Room 10", 1,
                    "2026-09-21T00:00:00Z", List.of(new AiServiceClient.Category(1, "INTERNET_NETWORK", "Internet & Network", "Network")), List.of());
        }
        @Override public void persistCompleted(long requestId, AiServiceClient.AnalysisResult result) { completed++; }
        @Override public void persistFailure(long requestId, String status) { failed++; failureStatus = status; }
    }
}
