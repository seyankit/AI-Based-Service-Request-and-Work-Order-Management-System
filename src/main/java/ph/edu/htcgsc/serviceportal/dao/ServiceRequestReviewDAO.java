package ph.edu.htcgsc.serviceportal.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.model.ServiceRequest;
import ph.edu.htcgsc.serviceportal.model.ServiceRequestReviewItem;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ServiceRequestReviewDAO {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAXIMUM_LIMIT = 100;

    public List<ServiceRequestReviewItem>
            findSubmittedRequests(
                    int limit
            ) throws SQLException {

        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException(
                    "The review limit must be from 1 to "
                            + MAXIMUM_LIMIT
                            + "."
            );
        }

        String sql = """
                SELECT
                    sr.Request_ID,
                    sr.Request_Number,
                    sr.Requester_ID,
                    sr.Requester_Department_ID,
                    sr.Requested_Category_ID,
                    sr.Final_Category_ID,
                    sr.Routed_Department_ID,
                    sr.Preferred_Priority,
                    sr.Final_Priority,
                    sr.Request_Title,
                    sr.Request_Description,
                    sr.Request_Location,
                    sr.Date_Reported,
                    sr.Current_Status,
                    sr.Completed_At,
                    sr.Closed_At,
                    sr.Created_At,
                    sr.Updated_At,

                    sp.First_Name
                        AS Requester_First_Name,
                    sp.Last_Name
                        AS Requester_Last_Name,
                    sp.Email
                        AS Requester_Email,
                    sp.Contact_Number
                        AS Requester_Contact_Number,
                    sp.Personnel_Type
                        AS Requester_Personnel_Type,

                    requester_department.Department_Name
                        AS Requester_Department_Name,

                    requested_category.Category_Code
                        AS Requested_Category_Code,
                    requested_category.Category_Name
                        AS Requested_Category_Name,

                    ai.Recommendation_ID
                        AS AI_Recommendation_ID,
                    ai.Recommendation_Sequence
                        AS AI_Recommendation_Sequence,
                    ai.Analysis_Status
                        AS AI_Analysis_Status,
                    ai.Recommendation_Method
                        AS AI_Recommendation_Method,
                    ai.Recommended_Category_ID
                        AS AI_Recommended_Category_ID,

                    recommended_category.Category_Name
                        AS AI_Recommended_Category_Name,

                    ai.Category_Confidence
                        AS AI_Category_Confidence,
                    ai.Recommended_Priority
                        AS AI_Recommended_Priority,
                    ai.Priority_Confidence
                        AS AI_Priority_Confidence,
                    ai.Possible_Duplicate_Request_ID
                        AS AI_Duplicate_Request_ID,

                    duplicate_request.Request_Number
                        AS AI_Duplicate_Request_Number,

                    ai.Duplicate_Similarity
                        AS AI_Duplicate_Similarity,
                    ai.Duplicate_Threshold
                        AS AI_Duplicate_Threshold,
                    ai.Duplicate_Candidates_JSON
                        AS AI_Duplicate_Candidates_JSON,
                    ai.Category_Explanation
                        AS AI_Category_Explanation,
                    ai.Priority_Explanation
                        AS AI_Priority_Explanation,
                    ai.Duplicate_Explanation
                        AS AI_Duplicate_Explanation,
                    ai.Analysis_Message
                        AS AI_Analysis_Message,
                    ai.Model_Name
                        AS AI_Model_Name,
                    ai.Model_Version
                        AS AI_Model_Version,
                    ai.Processing_Time_Ms
                        AS AI_Processing_Time_Ms,
                    ai.Generated_At
                        AS AI_Generated_At

                FROM SERVICE_REQUEST sr

                INNER JOIN SCHOOL_PERSONNEL sp
                    ON sp.Personnel_ID =
                       sr.Requester_ID

                INNER JOIN DEPARTMENT
                        requester_department
                    ON requester_department.Department_ID =
                       sr.Requester_Department_ID

                LEFT JOIN SERVICE_CATEGORY
                        requested_category
                    ON requested_category.Category_ID =
                       sr.Requested_Category_ID

                LEFT JOIN AI_RECOMMENDATION ai
                    ON ai.Recommendation_ID = (
                        SELECT ai_current.Recommendation_ID
                        FROM AI_RECOMMENDATION ai_current
                        WHERE ai_current.Request_ID =
                              sr.Request_ID
                          AND ai_current.Is_Current = TRUE
                        ORDER BY
                            ai_current.Recommendation_Sequence
                                DESC,
                            ai_current.Recommendation_ID DESC
                        LIMIT 1
                    )

                LEFT JOIN SERVICE_CATEGORY
                        recommended_category
                    ON recommended_category.Category_ID =
                       ai.Recommended_Category_ID

                LEFT JOIN SERVICE_REQUEST
                        duplicate_request
                    ON duplicate_request.Request_ID =
                       ai.Possible_Duplicate_Request_ID

                WHERE sr.Current_Status = 'Submitted'

                ORDER BY
                    CASE sr.Preferred_Priority
                        WHEN 'Urgent' THEN 1
                        WHEN 'High' THEN 2
                        WHEN 'Medium' THEN 3
                        WHEN 'Low' THEN 4
                        ELSE 5
                    END,
                    sr.Created_At ASC,
                    sr.Request_ID ASC

                LIMIT ?
                """;

        List<ServiceRequestReviewItem> reviewItems =
                new ArrayList<>();

        try (
            Connection connection =
                    DatabaseConnection.getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    limit
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                while (result.next()) {
                    reviewItems.add(
                            mapReviewItem(connection, result)
                    );
                }
            }
        }

        return reviewItems;
    }

    private ServiceRequestReviewItem mapReviewItem(
            Connection connection,
            ResultSet result
    ) throws SQLException {

        ServiceRequest request =
                mapServiceRequest(result);

        ServiceRequestReviewItem.RequesterSummary
                requester =
                new ServiceRequestReviewItem
                        .RequesterSummary(
                                result.getInt(
                                        "Requester_ID"
                                ),
                                result.getString(
                                        "Requester_First_Name"
                                ),
                                result.getString(
                                        "Requester_Last_Name"
                                ),
                                result.getString(
                                        "Requester_Email"
                                ),
                                result.getString(
                                        "Requester_Contact_Number"
                                ),
                                result.getString(
                                        "Requester_Personnel_Type"
                                ),
                                result.getInt(
                                        "Requester_Department_ID"
                                ),
                                result.getString(
                                        "Requester_Department_Name"
                                )
                        );

        Integer requestedCategoryId =
                getNullableInteger(
                        result,
                        "Requested_Category_ID"
                );

        ServiceRequestReviewItem.CategorySummary
                requestedCategory =
                requestedCategoryId == null
                        ? null
                        : new ServiceRequestReviewItem
                                .CategorySummary(
                                        requestedCategoryId,
                                        result.getString(
                                                "Requested_Category_Code"
                                        ),
                                        result.getString(
                                                "Requested_Category_Name"
                                        )
                                );

        Long recommendationId =
                getNullableLong(
                        result,
                        "AI_Recommendation_ID"
                );

        ServiceRequestReviewItem
                .AiRecommendationSummary
                aiRecommendation =
                null;

        if (recommendationId != null) {
            aiRecommendation =
                    new ServiceRequestReviewItem
                            .AiRecommendationSummary(
                                    recommendationId,
                                    getNullableInteger(
                                            result,
                                            "AI_Recommendation_Sequence"
                                    ),
                                    result.getString(
                                            "AI_Analysis_Status"
                                    ),
                                    result.getString(
                                            "AI_Recommendation_Method"
                                    ),
                                    getNullableInteger(
                                            result,
                                            "AI_Recommended_Category_ID"
                                    ),
                                    result.getString(
                                            "AI_Recommended_Category_Name"
                                    ),
                                    result.getBigDecimal(
                                            "AI_Category_Confidence"
                                    ),
                                    result.getString(
                                            "AI_Recommended_Priority"
                                    ),
                                    result.getBigDecimal(
                                            "AI_Priority_Confidence"
                                    ),
                                    getNullableLong(
                                            result,
                                            "AI_Duplicate_Request_ID"
                                    ),
                                    result.getString(
                                            "AI_Duplicate_Request_Number"
                                    ),
                                    result.getBigDecimal(
                                            "AI_Duplicate_Similarity"
                                    ),
                                    result.getBigDecimal(
                                            "AI_Duplicate_Threshold"
                                    ),
                                    result.getString(
                                            "AI_Category_Explanation"
                                    ),
                                    result.getString(
                                            "AI_Priority_Explanation"
                                    ),
                                    result.getString(
                                            "AI_Duplicate_Explanation"
                                    ),
                                    result.getString(
                                            "AI_Analysis_Message"
                                    ),
                                    result.getString(
                                            "AI_Model_Name"
                                    ),
                                    result.getString(
                                            "AI_Model_Version"
                                    ),
                                    getNullableInteger(
                                            result,
                                            "AI_Processing_Time_Ms"
                                    ),
                                    toIsoTimestamp(
                                            result.getTimestamp(
                                                    "AI_Generated_At"
                                            )
                                    ),
                                    loadRankedCandidates(
                                            connection,
                                            result.getString(
                                                    "AI_Duplicate_Candidates_JSON"
                                            )
                                    )
                            );
        }

        return new ServiceRequestReviewItem(
                request,
                requester,
                requestedCategory,
                aiRecommendation
        );
    }

    private List<ServiceRequestReviewItem.DuplicateCandidateSummary> loadRankedCandidates(
            Connection connection, String storedJson
    ) throws SQLException {
        List<StoredDuplicateCandidate> storedCandidates = parseRankedCandidates(storedJson);
        if (storedCandidates.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(", ", java.util.Collections.nCopies(storedCandidates.size(), "?"));
        String sql = """
                SELECT sr.Request_ID, sr.Request_Number, sr.Request_Title, sr.Current_Status,
                       category.Category_Name
                FROM SERVICE_REQUEST sr
                LEFT JOIN SERVICE_CATEGORY category ON category.Category_ID = sr.Requested_Category_ID
                WHERE sr.Request_ID IN (%s)
                """.formatted(placeholders);
        java.util.Map<Long, CurrentDuplicateCandidate> currentCandidates = new java.util.HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < storedCandidates.size(); index++) {
                statement.setLong(index + 1, storedCandidates.get(index).requestId());
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    currentCandidates.put(result.getLong("Request_ID"), new CurrentDuplicateCandidate(
                            result.getString("Request_Number"), result.getString("Request_Title"),
                            result.getString("Category_Name"), result.getString("Current_Status")
                    ));
                }
            }
        }

        List<ServiceRequestReviewItem.DuplicateCandidateSummary> results = new ArrayList<>();
        for (StoredDuplicateCandidate stored : storedCandidates) {
            CurrentDuplicateCandidate current = currentCandidates.get(stored.requestId());
            if (current == null || "Duplicate".equalsIgnoreCase(current.currentStatus())) {
                continue;
            }
            results.add(new ServiceRequestReviewItem.DuplicateCandidateSummary(
                    stored.requestId(), current.requestNumber(), current.title(), current.category(),
                    current.currentStatus(), stored.score(), stored.explanation()
            ));
        }
        return List.copyOf(results);
    }

    private List<StoredDuplicateCandidate> parseRankedCandidates(String storedJson) {
        if (storedJson == null || storedJson.isBlank()) {
            return List.of();
        }
        try {
            JsonElement root = JsonParser.parseString(storedJson);
            if (!root.isJsonArray()) {
                return List.of();
            }
            JsonArray values = root.getAsJsonArray();
            if (values.size() > 10) {
                return List.of();
            }
            Set<Long> ids = new HashSet<>();
            List<StoredDuplicateCandidate> results = new ArrayList<>();
            for (JsonElement value : values) {
                if (!value.isJsonObject()) {
                    continue;
                }
                JsonObject item = value.getAsJsonObject();
                if (!item.has("requestId") || !item.has("score") || !item.has("explanation")
                        || !item.get("requestId").isJsonPrimitive()
                        || !item.get("requestId").getAsJsonPrimitive().isNumber()
                        || !item.get("score").isJsonPrimitive()
                        || !item.get("score").getAsJsonPrimitive().isNumber()
                        || !item.get("explanation").isJsonPrimitive()
                        || !item.get("explanation").getAsJsonPrimitive().isString()) {
                    continue;
                }
                BigDecimal requestIdValue = new BigDecimal(item.get("requestId").getAsString());
                long requestId = requestIdValue.longValueExact();
                BigDecimal score = new BigDecimal(item.get("score").getAsString());
                String explanation = item.get("explanation").getAsString().trim();
                if (requestId <= 0 || requestIdValue.compareTo(BigDecimal.valueOf(requestId)) != 0 || !ids.add(requestId)
                        || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0
                        || explanation.length() > 1000) {
                    continue;
                }
                results.add(new StoredDuplicateCandidate(requestId, score, explanation));
            }
            return List.copyOf(results);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private record StoredDuplicateCandidate(long requestId, BigDecimal score, String explanation) {
    }

    private record CurrentDuplicateCandidate(String requestNumber, String title, String category,
                                             String currentStatus) {
    }

    private ServiceRequest mapServiceRequest(
            ResultSet result
    ) throws SQLException {

        ServiceRequest request =
                new ServiceRequest();

        request.setRequestId(
                result.getLong("Request_ID")
        );

        request.setRequestNumber(
                result.getString("Request_Number")
        );

        request.setRequesterId(
                result.getInt("Requester_ID")
        );

        request.setRequesterDepartmentId(
                result.getInt(
                        "Requester_Department_ID"
                )
        );

        request.setRequestedCategoryId(
                getNullableInteger(
                        result,
                        "Requested_Category_ID"
                )
        );

        request.setFinalCategoryId(
                getNullableInteger(
                        result,
                        "Final_Category_ID"
                )
        );

        request.setRoutedDepartmentId(
                getNullableInteger(
                        result,
                        "Routed_Department_ID"
                )
        );

        request.setPreferredPriority(
                result.getString(
                        "Preferred_Priority"
                )
        );

        request.setFinalPriority(
                result.getString(
                        "Final_Priority"
                )
        );

        request.setTitle(
                result.getString(
                        "Request_Title"
                )
        );

        request.setDescription(
                result.getString(
                        "Request_Description"
                )
        );

        request.setLocation(
                result.getString(
                        "Request_Location"
                )
        );

        request.setDateReported(
                toIsoDate(
                        result.getDate(
                                "Date_Reported"
                        )
                )
        );

        request.setCurrentStatus(
                result.getString(
                        "Current_Status"
                )
        );

        request.setCompletedAt(
                toIsoTimestamp(
                        result.getTimestamp(
                                "Completed_At"
                        )
                )
        );

        request.setClosedAt(
                toIsoTimestamp(
                        result.getTimestamp(
                                "Closed_At"
                        )
                )
        );

        request.setCreatedAt(
                toIsoTimestamp(
                        result.getTimestamp(
                                "Created_At"
                        )
                )
        );

        request.setUpdatedAt(
                toIsoTimestamp(
                        result.getTimestamp(
                                "Updated_At"
                        )
                )
        );

        return request;
    }

    private Integer getNullableInteger(
            ResultSet result,
            String columnName
    ) throws SQLException {

        int value =
                result.getInt(columnName);

        return result.wasNull()
                ? null
                : value;
    }

    private Long getNullableLong(
            ResultSet result,
            String columnName
    ) throws SQLException {

        long value =
                result.getLong(columnName);

        return result.wasNull()
                ? null
                : value;
    }

    private String toIsoDate(
            Date value
    ) {
        return value == null
                ? null
                : value.toLocalDate().toString();
    }

    private String toIsoTimestamp(
            Timestamp value
    ) {
        return value == null
                ? null
                : value.toLocalDateTime().toString();
    }
}
