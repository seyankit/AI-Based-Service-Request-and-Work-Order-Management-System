package ph.edu.htcgsc.serviceportal.service;

import java.math.BigDecimal;
import java.util.List;

public final class AiRecommendationService {

    public interface Repository {
        AiServiceClient.AnalysisInput loadInput(int actorPersonnelId, int actorRoleId, long requestId)
                throws Exception;
        void persistCompleted(long requestId, AiServiceClient.AnalysisResult result) throws Exception;
        void persistFailure(long requestId, String status) throws Exception;
    }

    @FunctionalInterface
    public interface Analyzer {
        Result analyze(int actorPersonnelId, int actorRoleId, long requestId) throws Exception;
    }

    public record Result(boolean available, String analysisStatus, String message,
                         long recommendedCategoryId, String recommendedCategoryName,
                         BigDecimal categoryConfidence, String recommendedPriority,
                         BigDecimal priorityConfidence, Long possibleDuplicateRequestId,
                         String possibleDuplicateRequestNumber, BigDecimal duplicateSimilarity,
                         List<AiServiceClient.DuplicateCandidate> duplicateCandidates,
                         String categoryExplanation, String priorityExplanation,
                         String duplicateExplanation, String recommendationMethod,
                         String modelName, String modelVersion) {
    }

    private final Repository repository;
    private final AiServiceClient client;

    public AiRecommendationService(Repository repository, AiServiceClient client) {
        this.repository = repository;
        this.client = client;
    }

    public Result analyze(int actorPersonnelId, int actorRoleId, long requestId) throws Exception {
        AiServiceClient.AnalysisInput input = repository.loadInput(actorPersonnelId, actorRoleId, requestId);
        try {
            AiServiceClient.AnalysisResult analysis = client.analyze(input);
            repository.persistCompleted(requestId, analysis);
            AiServiceClient.Category category = input.categories().stream()
                    .filter(value -> value.categoryId() == analysis.recommendedCategoryId())
                    .findFirst()
                    .orElseThrow();
            AiServiceClient.Candidate duplicate = analysis.possibleDuplicateRequestId() == null ? null
                    : input.candidates().stream()
                    .filter(value -> value.requestId() == analysis.possibleDuplicateRequestId())
                    .findFirst()
                    .orElse(null);
            return new Result(true, "Completed", "Advisory analysis is ready.",
                    analysis.recommendedCategoryId(), category.categoryName(), analysis.categoryConfidence(),
                    analysis.recommendedPriority(), analysis.priorityConfidence(),
                    analysis.possibleDuplicateRequestId(), duplicate == null ? null : duplicate.requestNumber(),
                    analysis.duplicateSimilarity(), analysis.duplicateCandidates(), analysis.categoryExplanation(), analysis.priorityExplanation(),
                    analysis.duplicateExplanation(), analysis.recommendationMethod(), analysis.modelName(),
                    analysis.modelVersion());
        } catch (AiServiceClient.UnavailableException exception) {
            repository.persistFailure(requestId, "Unavailable");
            return unavailable("Unavailable");
        } catch (AiServiceClient.InvalidResponseException exception) {
            repository.persistFailure(requestId, "Failed");
            return unavailable("Failed");
        }
    }

    private Result unavailable(String status) {
        return new Result(false, status, "Advisory analysis is currently unavailable.",
                0, null, null, null, null, null, null, null,
                List.of(), null, null, null, "None", null, null);
    }
}
