package ph.edu.htcgsc.serviceportal.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiServiceClientTest {

    private static final String TOKEN = "a-very-long-test-token-that-is-never-logged";

    @Test
    void validResponseIsAccepted() throws Exception {
        AiServiceClient client = client(200, response("Medium", 1, "[]"));
        AiServiceClient.AnalysisResult result = client.analyze(input());
        assertEquals("Medium", result.recommendedPriority());
        assertEquals(1, result.recommendedCategoryId());
    }

    @Test
    void unavailableAndNonSuccessResponsesAreSafe() {
        AiServiceClient offline = new AiServiceClient(config(), (uri, token, body) -> {
            throw new IOException("connection refused");
        });
        assertThrows(AiServiceClient.UnavailableException.class, () -> offline.analyze(input()));
        AiServiceClient nonSuccess = client(503, "{});");
        assertThrows(AiServiceClient.UnavailableException.class, () -> nonSuccess.analyze(input()));
    }

    @Test
    void malformedAndInvalidValuesAreRejectedWithoutTokenLeakage() {
        AiServiceClient malformed = client(200, "not-json");
        assertThrows(AiServiceClient.InvalidResponseException.class, () -> malformed.analyze(input()));

        AiServiceClient invalidPriority = client(200, response("Critical", 1, "[]"));
        Exception priorityError = assertThrows(AiServiceClient.InvalidResponseException.class,
                () -> invalidPriority.analyze(input()));
        assertFalse(String.valueOf(priorityError.getMessage()).contains(TOKEN));

        AiServiceClient invalidCategory = client(200, response("Medium", 99, "[]"));
        assertThrows(AiServiceClient.InvalidResponseException.class, () -> invalidCategory.analyze(input()));
    }

    @Test
    void foreignDuplicateCandidateIsRejected() {
        String candidates = "[{\"requestId\":88,\"similarity\":0.7,\"explanation\":\"Foreign candidate.\"}]";
        AiServiceClient client = client(200, response("Medium", 1, candidates)
                .replace("\"possibleDuplicateRequestId\":null", "\"possibleDuplicateRequestId\":88")
                .replace("\"duplicateSimilarity\":null", "\"duplicateSimilarity\":0.7"));
        assertThrows(AiServiceClient.InvalidResponseException.class, () -> client.analyze(input()));
    }

    @Test
    void nonLoopbackServiceIsRejectedBeforeAnyRequest() {
        AiServiceClient client = new AiServiceClient(
                new AiServiceClient.Configuration("http://example.test:8091", TOKEN),
                (uri, token, payload) -> { throw new AssertionError("transport must not run"); });
        assertThrows(AiServiceClient.UnavailableException.class, () -> client.analyze(input()));
    }

    private AiServiceClient client(int status, String body) {
        return new AiServiceClient(config(), (uri, token, payload) -> new AiServiceClient.RawResponse(status, body));
    }

    private AiServiceClient.Configuration config() {
        return new AiServiceClient.Configuration("http://127.0.0.1:8091", TOKEN);
    }

    private AiServiceClient.AnalysisInput input() {
        return new AiServiceClient.AnalysisInput(7, "Network outage", "The internet is not working.",
                "Room 10", 1, "2026-09-21T00:00:00Z",
                List.of(new AiServiceClient.Category(1, "INTERNET_NETWORK", "Internet & Network", "Network issues.")),
                List.of(new AiServiceClient.Candidate(8, "SR-2026-000008", "Older network outage",
                        "Internet unavailable.", "Room 10", 1, "2026-09-20T00:00:00Z", "Submitted")));
    }

    private String response(String priority, long categoryId, String candidates) {
        return "{\"success\":true,\"data\":{"
                + "\"analysisStatus\":\"Completed\",\"recommendationMethod\":\"Rule-Based\","
                + "\"recommendedCategoryId\":" + categoryId + ",\"categoryConfidence\":0.5,"
                + "\"recommendedPriority\":\"" + priority + "\",\"priorityConfidence\":0.5,"
                + "\"possibleDuplicateRequestId\":null,\"duplicateSimilarity\":null,\"duplicateThreshold\":0.58,"
                + "\"duplicateCandidates\":" + candidates + ",\"categoryExplanation\":\"Category reason.\","
                + "\"priorityExplanation\":\"Priority reason.\",\"duplicateExplanation\":\"No duplicate.\","
                + "\"analysisMessage\":\"Advisory only.\",\"modelName\":\"Rules\",\"modelVersion\":\"1\","
                + "\"generatedAt\":\"2026-09-21T00:00:00+00:00\",\"processingTimeMs\":1}}";
    }
}
