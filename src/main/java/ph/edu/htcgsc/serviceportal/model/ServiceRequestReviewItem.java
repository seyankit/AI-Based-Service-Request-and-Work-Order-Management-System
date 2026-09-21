package ph.edu.htcgsc.serviceportal.model;

import java.math.BigDecimal;
import java.util.List;

public record ServiceRequestReviewItem(
        ServiceRequest request,
        RequesterSummary requester,
        CategorySummary requestedCategory,
        AiRecommendationSummary aiRecommendation
) {

    public record RequesterSummary(
            int personnelId,
            String firstName,
            String lastName,
            String email,
            String contactNumber,
            String personnelType,
            int departmentId,
            String departmentName
    ) {
    }

    public record CategorySummary(
            Integer categoryId,
            String categoryCode,
            String categoryName
    ) {
    }

    public record AiRecommendationSummary(
            Long recommendationId,
            Integer recommendationSequence,
            String analysisStatus,
            String recommendationMethod,
            Integer recommendedCategoryId,
            String recommendedCategoryName,
            BigDecimal categoryConfidence,
            String recommendedPriority,
            BigDecimal priorityConfidence,
            Long possibleDuplicateRequestId,
            String possibleDuplicateRequestNumber,
            BigDecimal duplicateSimilarity,
            BigDecimal duplicateThreshold,
            String categoryExplanation,
            String priorityExplanation,
            String duplicateExplanation,
            String analysisMessage,
            String modelName,
            String modelVersion,
            Integer processingTimeMs,
            String generatedAt,
            List<DuplicateCandidateSummary> duplicateCandidates
    ) {
    }

    public record DuplicateCandidateSummary(
            long requestId,
            String requestNumber,
            String title,
            String category,
            String currentStatus,
            BigDecimal similarityScore,
            String explanation
    ) {
    }
}
