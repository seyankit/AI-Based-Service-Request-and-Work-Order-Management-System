package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class ServiceRequestDuplicateDAO {

    private static final int SERVICE_ADMINISTRATOR_ROLE_ID = 2;

    public record DuplicateResult(
            long duplicateLinkId,
            long duplicateRequestId,
            String duplicateRequestNumber,
            long originalRequestId,
            String originalRequestNumber,
            String previousStatus,
            String newStatus
    ) {
    }

    private record RequestState(
            long requestId,
            String requestNumber,
            String currentStatus
    ) {
    }

    public DuplicateResult markDuplicate(
            int actorPersonnelId,
            int actorRoleId,
            long duplicateRequestId,
            long originalRequestId,
            String confirmationReason,
            String clientIpAddress,
            String clientUserAgent
    ) throws SQLException {

        if (actorPersonnelId <= 0) {
            throw new SecurityException(
                    "Authentication is required."
            );
        }

        if (duplicateRequestId <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid duplicate request."
            );
        }

        if (originalRequestId <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid original request."
            );
        }

        if (duplicateRequestId == originalRequestId) {
            throw new IllegalArgumentException(
                    "A request cannot be marked as a duplicate of itself."
            );
        }

        String normalizedReason =
                normalizeReason(
                        confirmationReason
                );

        try (
            Connection connection =
                    DatabaseConnection.getConnection()
        ) {
            boolean originalAutoCommit =
                    connection.getAutoCommit();

            connection.setAutoCommit(false);

            try {
                requireActiveServiceAdministrator(
                        connection,
                        actorPersonnelId,
                        actorRoleId
                );

                RequestState duplicateRequest =
                        loadAndLockRequest(
                                connection,
                                duplicateRequestId
                        );

                RequestState originalRequest =
                        loadAndLockRequest(
                                connection,
                                originalRequestId
                        );

                if (
                    !"Submitted".equalsIgnoreCase(
                            duplicateRequest.currentStatus()
                    )
                ) {
                    throw new IllegalStateException(
                            "Only Submitted requests can be marked as duplicates from the review queue."
                    );
                }

                if (
                    "Duplicate".equalsIgnoreCase(
                            originalRequest.currentStatus()
                    )
                ) {
                    throw new IllegalArgumentException(
                            "Select the original request rather than another duplicate request."
                    );
                }

                requireNoExistingDuplicateLink(
                        connection,
                        duplicateRequestId
                );

                Long recommendationId =
                        findMatchingCurrentRecommendation(
                                connection,
                                duplicateRequestId,
                                originalRequestId
                        );

                long duplicateLinkId =
                        insertDuplicateLink(
                                connection,
                                duplicateRequestId,
                                originalRequestId,
                                recommendationId,
                                actorPersonnelId,
                                normalizedReason
                        );

                updateRequestStatus(
                        connection,
                        duplicateRequestId
                );

                insertStatusHistory(
                        connection,
                        duplicateRequest,
                        actorPersonnelId,
                        actorRoleId,
                        originalRequest,
                        normalizedReason
                );

                insertAuditLog(
                        connection,
                        actorPersonnelId,
                        actorRoleId,
                        duplicateRequest,
                        originalRequest,
                        duplicateLinkId,
                        recommendationId,
                        normalizedReason,
                        clientIpAddress,
                        clientUserAgent
                );

                connection.commit();

                return new DuplicateResult(
                        duplicateLinkId,
                        duplicateRequest.requestId(),
                        duplicateRequest.requestNumber(),
                        originalRequest.requestId(),
                        originalRequest.requestNumber(),
                        duplicateRequest.currentStatus(),
                        "Duplicate"
                );

            } catch (
                    SQLException
                    | IllegalArgumentException
                    | IllegalStateException
                    | SecurityException exception
            ) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(
                            rollbackException
                    );
                }

                throw exception;

            } finally {
                try {
                    connection.setAutoCommit(
                            originalAutoCommit
                    );
                } catch (SQLException ignored) {
                    // Closing the connection is the final safeguard.
                }
            }
        }
    }

    public record OriginalRequestCandidate(
            long requestId,
            String requestNumber,
            String title,
            String currentStatus
    ) {
    }

    public java.util.List<OriginalRequestCandidate>
            findOriginalRequestCandidates(
                    long duplicateRequestId
            ) throws SQLException {

        if (duplicateRequestId <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid duplicate request."
            );
        }

        String sql = """
                SELECT
                    Request_ID,
                    Request_Number,
                    Request_Title,
                    Current_Status
                FROM SERVICE_REQUEST
                WHERE Request_ID <> ?
                  AND Current_Status <> 'Duplicate'
                ORDER BY Created_At DESC, Request_ID DESC
                LIMIT 100
                """;

        java.util.List<OriginalRequestCandidate> results =
                new java.util.ArrayList<>();

        try (
            Connection connection =
                    DatabaseConnection.getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setLong(1, duplicateRequestId);

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                while (resultSet.next()) {
                    results.add(
                            new OriginalRequestCandidate(
                                    resultSet.getLong("Request_ID"),
                                    resultSet.getString("Request_Number"),
                                    resultSet.getString("Request_Title"),
                                    resultSet.getString("Current_Status")
                            )
                    );
                }
            }
        }

        return results;
    }
    private void requireActiveServiceAdministrator(
            Connection connection,
            int actorPersonnelId,
            int sessionRoleId
    ) throws SQLException {

        if (
            sessionRoleId !=
            SERVICE_ADMINISTRATOR_ROLE_ID
        ) {
            throw new SecurityException(
                    "Service Administrator access is required."
            );
        }

        String sql = """
                SELECT
                    sp.Account_Status,
                    pra.Role_ID
                FROM SCHOOL_PERSONNEL sp
                INNER JOIN PERSONNEL_ROLE_ASSIGNMENT pra
                    ON pra.Personnel_ID =
                       sp.Personnel_ID
                WHERE sp.Personnel_ID = ?
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    actorPersonnelId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    throw new SecurityException(
                            "The administrator account is no longer available."
                    );
                }

                String accountStatus =
                        resultSet.getString(
                                "Account_Status"
                        );

                int databaseRoleId =
                        resultSet.getInt(
                                "Role_ID"
                        );

                if (
                    databaseRoleId !=
                    SERVICE_ADMINISTRATOR_ROLE_ID
                    || !"Active".equalsIgnoreCase(
                            String.valueOf(
                                    accountStatus
                            )
                    )
                ) {
                    throw new SecurityException(
                            "The current account no longer has active Service Administrator access."
                    );
                }
            }
        }
    }

    private RequestState loadAndLockRequest(
            Connection connection,
            long requestId
    ) throws SQLException {

        String sql = """
                SELECT
                    Request_ID,
                    Request_Number,
                    Current_Status
                FROM SERVICE_REQUEST
                WHERE Request_ID = ?
                FOR UPDATE
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setLong(
                    1,
                    requestId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "The selected service request does not exist."
                    );
                }

                return new RequestState(
                        resultSet.getLong(
                                "Request_ID"
                        ),
                        resultSet.getString(
                                "Request_Number"
                        ),
                        resultSet.getString(
                                "Current_Status"
                        )
                );
            }
        }
    }

    private void requireNoExistingDuplicateLink(
            Connection connection,
            long duplicateRequestId
    ) throws SQLException {

        String sql = """
                SELECT Duplicate_Link_ID
                FROM REQUEST_DUPLICATE_LINK
                WHERE Duplicate_Request_ID = ?
                LIMIT 1
                FOR UPDATE
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setLong(
                    1,
                    duplicateRequestId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (resultSet.next()) {
                    throw new IllegalStateException(
                            "This request is already linked as a duplicate."
                    );
                }
            }
        }
    }

    private Long findMatchingCurrentRecommendation(
            Connection connection,
            long duplicateRequestId,
            long originalRequestId
    ) throws SQLException {

        String sql = """
                SELECT Recommendation_ID
                FROM AI_RECOMMENDATION
                WHERE Request_ID = ?
                  AND Is_Current = TRUE
                  AND Possible_Duplicate_Request_ID = ?
                ORDER BY
                    Recommendation_Sequence DESC,
                    Recommendation_ID DESC
                LIMIT 1
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setLong(
                    1,
                    duplicateRequestId
            );

            statement.setLong(
                    2,
                    originalRequestId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                return resultSet.next()
                        ? resultSet.getLong(
                                "Recommendation_ID"
                        )
                        : null;
            }
        }
    }

    private long insertDuplicateLink(
            Connection connection,
            long duplicateRequestId,
            long originalRequestId,
            Long recommendationId,
            int confirmedById,
            String confirmationReason
    ) throws SQLException {

        String sql = """
                INSERT INTO REQUEST_DUPLICATE_LINK (
                    Duplicate_Request_ID,
                    Original_Request_ID,
                    Recommendation_ID,
                    Confirmed_By_ID,
                    Confirmation_Reason
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            Statement.RETURN_GENERATED_KEYS
                    )
        ) {
            statement.setLong(
                    1,
                    duplicateRequestId
            );

            statement.setLong(
                    2,
                    originalRequestId
            );

            statement.setObject(
                    3,
                    recommendationId
            );

            statement.setInt(
                    4,
                    confirmedById
            );

            statement.setString(
                    5,
                    confirmationReason
            );

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "The duplicate relationship could not be created."
                );
            }

            try (
                ResultSet generatedKeys =
                        statement.getGeneratedKeys()
            ) {
                if (!generatedKeys.next()) {
                    throw new SQLException(
                            "The duplicate-link identifier was not returned."
                    );
                }

                return generatedKeys.getLong(1);
            }
        }
    }

    private void updateRequestStatus(
            Connection connection,
            long requestId
    ) throws SQLException {

        String sql = """
                UPDATE SERVICE_REQUEST
                SET Current_Status = 'Duplicate'
                WHERE Request_ID = ?
                  AND Current_Status = 'Submitted'
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setLong(
                    1,
                    requestId
            );

            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "The request is no longer available for duplicate confirmation."
                );
            }
        }
    }

    private void insertStatusHistory(
            Connection connection,
            RequestState duplicateRequest,
            int actorPersonnelId,
            int actorRoleId,
            RequestState originalRequest,
            String confirmationReason
    ) throws SQLException {

        String sql = """
                INSERT INTO REQUEST_STATUS_HISTORY (
                    Request_ID,
                    Previous_Status,
                    New_Status,
                    Changed_By,
                    Changed_By_Role_ID,
                    Change_Reason
                )
                VALUES (
                    ?,
                    ?,
                    'Duplicate',
                    ?,
                    ?,
                    ?
                )
                """;

        String reason =
                "Confirmed as duplicate of "
                + originalRequest.requestNumber()
                + ". "
                + confirmationReason;

        if (reason.length() > 1000) {
            reason =
                    reason.substring(
                            0,
                            1000
                    );
        }

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setLong(
                    1,
                    duplicateRequest.requestId()
            );

            statement.setString(
                    2,
                    duplicateRequest.currentStatus()
            );

            statement.setInt(
                    3,
                    actorPersonnelId
            );

            statement.setInt(
                    4,
                    actorRoleId
            );

            statement.setString(
                    5,
                    reason
            );

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "The duplicate status history could not be recorded."
                );
            }
        }
    }

    private void insertAuditLog(
            Connection connection,
            int actorPersonnelId,
            int actorRoleId,
            RequestState duplicateRequest,
            RequestState originalRequest,
            long duplicateLinkId,
            Long recommendationId,
            String confirmationReason,
            String clientIpAddress,
            String clientUserAgent
    ) throws SQLException {

        String sql = """
                INSERT INTO AUDIT_LOG (
                    Actor_Type,
                    Actor_ID,
                    Actor_Role_ID,
                    Request_ID,
                    Work_Order_ID,
                    Action_Type,
                    Entity_Type,
                    Entity_ID,
                    Action_Outcome,
                    Action_Summary,
                    Details_JSON,
                    Client_IP_Address,
                    Client_User_Agent,
                    Correlation_ID
                )
                VALUES (
                    'PERSONNEL',
                    ?,
                    ?,
                    ?,
                    NULL,
                    'SERVICE_REQUEST_MARKED_DUPLICATE',
                    'REQUEST_DUPLICATE_LINK',
                    ?,
                    'SUCCESS',
                    ?,
                    JSON_OBJECT(
                        'duplicateRequestId', ?,
                        'duplicateRequestNumber', ?,
                        'originalRequestId', ?,
                        'originalRequestNumber', ?,
                        'recommendationId', ?,
                        'confirmationReason', ?
                    ),
                    ?,
                    ?,
                    ?
                )
                """;

        String summary =
                "Service Administrator marked "
                + duplicateRequest.requestNumber()
                + " as a duplicate of "
                + originalRequest.requestNumber()
                + ".";

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(1, actorPersonnelId);
            statement.setInt(2, actorRoleId);
            statement.setLong(
                    3,
                    duplicateRequest.requestId()
            );
            statement.setLong(
                    4,
                    duplicateLinkId
            );
            statement.setString(
                    5,
                    summary
            );
            statement.setLong(
                    6,
                    duplicateRequest.requestId()
            );
            statement.setString(
                    7,
                    duplicateRequest.requestNumber()
            );
            statement.setLong(
                    8,
                    originalRequest.requestId()
            );
            statement.setString(
                    9,
                    originalRequest.requestNumber()
            );
            statement.setObject(
                    10,
                    recommendationId
            );
            statement.setString(
                    11,
                    confirmationReason
            );
            statement.setString(
                    12,
                    limitNullableText(
                            clientIpAddress,
                            45
                    )
            );
            statement.setString(
                    13,
                    limitNullableText(
                            clientUserAgent,
                            500
                    )
            );
            statement.setString(
                    14,
                    UUID.randomUUID()
                            .toString()
            );

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "The duplicate audit record could not be written."
                );
            }
        }
    }

    private String normalizeReason(
            String confirmationReason
    ) {
        if (confirmationReason == null) {
            throw new IllegalArgumentException(
                    "A duplicate confirmation reason is required."
            );
        }

        String reason =
                confirmationReason.trim();

        if (reason.length() < 5) {
            throw new IllegalArgumentException(
                    "The duplicate confirmation reason must contain at least 5 characters."
            );
        }

        if (reason.length() > 1000) {
            throw new IllegalArgumentException(
                    "The duplicate confirmation reason must not exceed 1000 characters."
            );
        }

        return reason;
    }

    private String limitNullableText(
            String value,
            int maximumLength
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.length()
                <= maximumLength
                ? trimmed
                : trimmed.substring(
                        0,
                        maximumLength
                );
    }
}