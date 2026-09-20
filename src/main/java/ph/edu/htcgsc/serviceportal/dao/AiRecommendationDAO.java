package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.service.AiRecommendationService;
import ph.edu.htcgsc.serviceportal.service.AiServiceClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Database authority for the advisory analysis input and persisted result. */
public final class AiRecommendationDAO implements AiRecommendationService.Repository {

    private static final int SERVICE_ADMINISTRATOR_ROLE_ID = 2;
    private static final int MAXIMUM_DUPLICATE_CANDIDATES = 200;

    public static final class RequestNotFoundException extends Exception {
        public RequestNotFoundException() {
            super("The selected service request does not exist.");
        }
    }

    public static final class IneligibleRequestException extends Exception {
        public IneligibleRequestException() {
            super("Only Submitted requests can receive advisory analysis.");
        }
    }

    @Override
    public AiServiceClient.AnalysisInput loadInput(int actorPersonnelId, int actorRoleId, long requestId)
            throws SQLException, SecurityException, RequestNotFoundException, IneligibleRequestException {
        if (requestId <= 0) {
            throw new IllegalArgumentException("Select a valid service request.");
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            requireActiveServiceAdministrator(connection, actorPersonnelId, actorRoleId);
            RequestInput request = loadRequest(connection, requestId);
            if (request == null) {
                throw new RequestNotFoundException();
            }
            if (!"Submitted".equals(request.status())) {
                throw new IneligibleRequestException();
            }
            return new AiServiceClient.AnalysisInput(
                    request.requestId(), request.title(), request.description(), request.location(),
                    request.requestedCategoryId(), toIsoTimestamp(request.createdAt()),
                    loadActiveCategories(connection), loadCandidates(connection, requestId)
            );
        }
    }

    @Override
    public void persistCompleted(long requestId, AiServiceClient.AnalysisResult analysis) throws Exception {
        updateCurrentRecommendation(requestId, "Completed", analysis);
    }

    @Override
    public void persistFailure(long requestId, String status) throws Exception {
        if (!"Unavailable".equals(status) && !"Failed".equals(status)) {
            throw new IllegalArgumentException("Invalid advisory status.");
        }
        updateCurrentRecommendation(requestId, status, null);
    }

    private void updateCurrentRecommendation(long requestId, String status,
                                             AiServiceClient.AnalysisResult analysis) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                requireSubmittedRequestForUpdate(connection, requestId);
                Long recommendationId = findCurrentRecommendationForUpdate(connection, requestId);
                if (recommendationId == null) {
                    recommendationId = insertCurrentRecommendation(connection, requestId);
                }
                if (analysis == null) {
                    updateUnavailableRecommendation(connection, recommendationId, status);
                } else {
                    updateCompletedRecommendation(connection, recommendationId, analysis);
                }
                connection.commit();
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            } finally {
                try {
                    connection.setAutoCommit(autoCommit);
                } catch (SQLException ignored) {
                    // Closing the connection is the final safeguard.
                }
            }
        }
    }

    private void requireActiveServiceAdministrator(Connection connection, int personnelId, int roleId)
            throws SQLException {
        if (personnelId <= 0 || roleId != SERVICE_ADMINISTRATOR_ROLE_ID) {
            throw new SecurityException("Service Administrator access is required.");
        }
        String sql = """
                SELECT sp.Account_Status
                FROM SCHOOL_PERSONNEL sp
                INNER JOIN PERSONNEL_ROLE_ASSIGNMENT pra ON pra.Personnel_ID = sp.Personnel_ID
                WHERE sp.Personnel_ID = ? AND pra.Role_ID = ?
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, personnelId);
            statement.setInt(2, SERVICE_ADMINISTRATOR_ROLE_ID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !"Active".equalsIgnoreCase(result.getString("Account_Status"))) {
                    throw new SecurityException("The current account no longer has active Service Administrator access.");
                }
            }
        }
    }

    private RequestInput loadRequest(Connection connection, long requestId) throws SQLException {
        String sql = """
                SELECT Request_ID, Request_Title, Request_Description, Request_Location,
                       Requested_Category_ID, Created_At, Current_Status
                FROM SERVICE_REQUEST WHERE Request_ID = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                int categoryId = result.getInt("Requested_Category_ID");
                boolean categoryMissing = result.wasNull();
                return new RequestInput(result.getLong("Request_ID"), result.getString("Request_Title"),
                        result.getString("Request_Description"), result.getString("Request_Location"),
                        categoryMissing ? null : categoryId, result.getTimestamp("Created_At"),
                        result.getString("Current_Status"));
            }
        }
    }

    private List<AiServiceClient.Category> loadActiveCategories(Connection connection) throws SQLException {
        String sql = """
                SELECT Category_ID, Category_Code, Category_Name, Category_Description
                FROM SERVICE_CATEGORY WHERE Is_Active = TRUE
                ORDER BY Display_Order, Category_ID
                """;
        List<AiServiceClient.Category> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                results.add(new AiServiceClient.Category(result.getLong("Category_ID"),
                        result.getString("Category_Code"), result.getString("Category_Name"),
                        result.getString("Category_Description")));
            }
        }
        if (results.isEmpty()) throw new SQLException("No active service categories are available.");
        return List.copyOf(results);
    }

    private List<AiServiceClient.Candidate> loadCandidates(Connection connection, long requestId) throws SQLException {
        String sql = """
                SELECT Request_ID, Request_Number, Request_Title, Request_Description,
                       Request_Location, Requested_Category_ID, Created_At, Current_Status
                FROM SERVICE_REQUEST
                WHERE Request_ID <> ?
                  AND Current_Status NOT IN ('Rejected', 'Completed', 'Closed', 'Cancelled', 'Duplicate')
                  AND Created_At >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 90 DAY)
                ORDER BY Created_At DESC, Request_ID DESC
                LIMIT ?
                """;
        List<AiServiceClient.Candidate> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            statement.setInt(2, MAXIMUM_DUPLICATE_CANDIDATES);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int categoryId = result.getInt("Requested_Category_ID");
                    boolean categoryMissing = result.wasNull();
                    results.add(new AiServiceClient.Candidate(result.getLong("Request_ID"),
                            result.getString("Request_Number"), result.getString("Request_Title"),
                            result.getString("Request_Description"), result.getString("Request_Location"),
                            categoryMissing ? null : categoryId, toIsoTimestamp(result.getTimestamp("Created_At")),
                            result.getString("Current_Status")));
                }
            }
        }
        return List.copyOf(results);
    }

    private void requireSubmittedRequestForUpdate(Connection connection, long requestId)
            throws SQLException, RequestNotFoundException, IneligibleRequestException {
        String sql = "SELECT Current_Status FROM SERVICE_REQUEST WHERE Request_ID = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new RequestNotFoundException();
                if (!"Submitted".equals(result.getString("Current_Status"))) throw new IneligibleRequestException();
            }
        }
    }

    private Long findCurrentRecommendationForUpdate(Connection connection, long requestId) throws SQLException {
        String sql = """
                SELECT Recommendation_ID FROM AI_RECOMMENDATION
                WHERE Request_ID = ? AND Is_Current = TRUE
                ORDER BY Recommendation_Sequence DESC, Recommendation_ID DESC LIMIT 1 FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("Recommendation_ID") : null;
            }
        }
    }

    private long insertCurrentRecommendation(Connection connection, long requestId) throws SQLException {
        String sql = """
                INSERT INTO AI_RECOMMENDATION (Request_ID, Recommendation_Sequence, Is_Current,
                    Analysis_Status, Recommendation_Method, Analysis_Message)
                SELECT ?, COALESCE(MAX(Recommendation_Sequence), 0) + 1, TRUE,
                    'Pending', 'None', 'Awaiting advisory AI analysis.'
                FROM AI_RECOMMENDATION WHERE Request_ID = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, requestId);
            statement.setLong(2, requestId);
            if (statement.executeUpdate() != 1) throw new SQLException("Unable to create an advisory recommendation.");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Unable to obtain the advisory recommendation ID.");
                return keys.getLong(1);
            }
        }
    }

    private void updateCompletedRecommendation(Connection connection, long recommendationId,
                                               AiServiceClient.AnalysisResult value) throws SQLException {
        String sql = """
                UPDATE AI_RECOMMENDATION SET Analysis_Status = 'Completed',
                    Recommendation_Method = ?, Recommended_Category_ID = ?, Category_Confidence = ?,
                    Recommended_Priority = ?, Priority_Confidence = ?, Possible_Duplicate_Request_ID = ?,
                    Duplicate_Similarity = ?, Duplicate_Threshold = ?, Category_Explanation = ?,
                    Priority_Explanation = ?, Duplicate_Explanation = ?, Analysis_Message = ?,
                    Model_Name = ?, Model_Version = ?, Processing_Time_Ms = ?, Generated_At = CURRENT_TIMESTAMP
                WHERE Recommendation_ID = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value.recommendationMethod());
            statement.setLong(2, value.recommendedCategoryId());
            statement.setBigDecimal(3, value.categoryConfidence());
            statement.setString(4, value.recommendedPriority());
            statement.setBigDecimal(5, value.priorityConfidence());
            statement.setObject(6, value.possibleDuplicateRequestId());
            statement.setBigDecimal(7, value.duplicateSimilarity());
            statement.setBigDecimal(8, value.duplicateThreshold());
            statement.setString(9, value.categoryExplanation());
            statement.setString(10, value.priorityExplanation());
            statement.setString(11, value.duplicateExplanation());
            statement.setString(12, value.analysisMessage());
            statement.setString(13, value.modelName());
            statement.setString(14, value.modelVersion());
            statement.setInt(15, value.processingTimeMs());
            statement.setLong(16, recommendationId);
            requireOneUpdatedRow(statement);
        }
    }

    private void updateUnavailableRecommendation(Connection connection, long recommendationId, String status) throws SQLException {
        String sql = """
                UPDATE AI_RECOMMENDATION SET Analysis_Status = ?, Recommendation_Method = 'None',
                    Recommended_Category_ID = NULL, Category_Confidence = NULL,
                    Recommended_Priority = NULL, Priority_Confidence = NULL,
                    Possible_Duplicate_Request_ID = NULL, Duplicate_Similarity = NULL,
                    Duplicate_Threshold = NULL, Category_Explanation = NULL,
                    Priority_Explanation = NULL, Duplicate_Explanation = NULL,
                    Analysis_Message = 'Advisory analysis is currently unavailable.', Model_Name = NULL,
                    Model_Version = NULL, Processing_Time_Ms = NULL, Generated_At = NULL
                WHERE Recommendation_ID = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, recommendationId);
            requireOneUpdatedRow(statement);
        }
    }

    private void requireOneUpdatedRow(PreparedStatement statement) throws SQLException {
        if (statement.executeUpdate() != 1) throw new SQLException("The advisory recommendation was not updated.");
    }

    private String toIsoTimestamp(Timestamp value) {
        return value.toInstant().toString();
    }

    private record RequestInput(long requestId, String title, String description, String location,
                                Integer requestedCategoryId, Timestamp createdAt, String status) {
    }
}
