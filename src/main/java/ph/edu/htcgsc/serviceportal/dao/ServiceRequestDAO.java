package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.dto.CreateServiceRequestRequest;
import ph.edu.htcgsc.serviceportal.model.ServiceRequest;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

public class ServiceRequestDAO {

    private static final int REQUESTER_ROLE_ID = 1;

    private static final String INITIAL_STATUS =
            "Submitted";

    private static final ZoneId PORTAL_TIME_ZONE =
            ZoneId.of("Asia/Manila");

    public enum CreationFailureReason {
        PERSONNEL_NOT_FOUND,
        ACCOUNT_NOT_ACTIVE,
        REQUESTER_ROLE_REQUIRED,
        MULTIPLE_ROLE_ASSIGNMENTS,
        CATEGORY_NOT_AVAILABLE
    }

    public static final class CreationException
            extends Exception {

        private final CreationFailureReason reason;

        public CreationException(
                CreationFailureReason reason,
                String message
        ) {
            super(message);
            this.reason = reason;
        }

        public CreationFailureReason getReason() {
            return reason;
        }
    }

    public ServiceRequest createServiceRequest(
            CreateServiceRequestRequest request,
            int authenticatedPersonnelId,
            String clientIpAddress,
            String clientUserAgent
    ) throws SQLException, CreationException {

        if (request == null) {
            throw new IllegalArgumentException(
                    "The service request is required."
            );
        }

        if (authenticatedPersonnelId <= 0) {
            throw new IllegalArgumentException(
                    "The authenticated personnel ID is invalid."
            );
        }

        try (Connection connection =
                     DatabaseConnection.getConnection()) {

            boolean originalAutoCommit =
                    connection.getAutoCommit();

            connection.setAutoCommit(false);

            try {
                RequesterContext requester =
                        loadAndLockRequester(
                                connection,
                                authenticatedPersonnelId
                        );

                verifyRequestedCategory(
                        connection,
                        request.getRequestedCategoryId()
                );

                int sequenceYear =
                        LocalDate.now(PORTAL_TIME_ZONE)
                                .getYear();

                long sequenceNumber =
                        obtainNextRequestNumber(
                                connection,
                                sequenceYear
                        );

                String requestNumber =
                        formatRequestNumber(
                                sequenceYear,
                                sequenceNumber
                        );

                long requestId =
                        insertServiceRequest(
                                connection,
                                request,
                                requestNumber,
                                authenticatedPersonnelId,
                                requester.departmentId()
                        );

                insertInitialStatusHistory(
                        connection,
                        requestId,
                        authenticatedPersonnelId,
                        requester.roleId()
                );

                insertPendingAiRecommendation(
                        connection,
                        requestId
                );

                insertSubmissionNotification(
                        connection,
                        requestId,
                        requestNumber,
                        authenticatedPersonnelId
                );

                insertSubmissionAuditLog(
                        connection,
                        requestId,
                        requestNumber,
                        request,
                        authenticatedPersonnelId,
                        requester.roleId(),
                        clientIpAddress,
                        clientUserAgent
                );

                connection.commit();

                return buildCreatedRequest(
                        request,
                        requestId,
                        requestNumber,
                        authenticatedPersonnelId,
                        requester.departmentId()
                );

            } catch (
                    SQLException
                    | CreationException
                    | RuntimeException exception
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
                    // Closing the connection remains the final safeguard.
                }
            }
        }
    }

    private RequesterContext loadAndLockRequester(
            Connection connection,
            int personnelId
    ) throws SQLException, CreationException {

        String sql = """
                SELECT
                    sp.Department_ID,
                    sp.Account_Status,
                    pra.Role_ID
                FROM SCHOOL_PERSONNEL sp
                INNER JOIN PERSONNEL_ROLE_ASSIGNMENT pra
                    ON pra.Personnel_ID = sp.Personnel_ID
                WHERE sp.Personnel_ID = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    personnelId
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    throw new CreationException(
                            CreationFailureReason.PERSONNEL_NOT_FOUND,
                            "The authenticated personnel account was not found."
                    );
                }

                int departmentId =
                        result.getInt("Department_ID");

                String accountStatus =
                        result.getString("Account_Status");

                int roleId =
                        result.getInt("Role_ID");

                if (result.next()) {
                    throw new CreationException(
                            CreationFailureReason.MULTIPLE_ROLE_ASSIGNMENTS,
                            "The account has more than one system role assignment."
                    );
                }

                if (
                    accountStatus == null
                    || !accountStatus.equalsIgnoreCase("Active")
                ) {
                    throw new CreationException(
                            CreationFailureReason.ACCOUNT_NOT_ACTIVE,
                            "The personnel account is not active."
                    );
                }

                if (roleId != REQUESTER_ROLE_ID) {
                    throw new CreationException(
                            CreationFailureReason.REQUESTER_ROLE_REQUIRED,
                            "Only authorized requester accounts may submit service requests."
                    );
                }

                return new RequesterContext(
                        departmentId,
                        roleId
                );
            }
        }
    }

    private void verifyRequestedCategory(
            Connection connection,
            Integer requestedCategoryId
    ) throws SQLException, CreationException {

        if (requestedCategoryId == null) {
            return;
        }

        String sql = """
                SELECT 1
                FROM SERVICE_CATEGORY
                WHERE Category_ID = ?
                  AND Is_Active = TRUE
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    requestedCategoryId
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    throw new CreationException(
                            CreationFailureReason.CATEGORY_NOT_AVAILABLE,
                            "The selected service category is not available."
                    );
                }
            }
        }
    }

    private long obtainNextRequestNumber(
            Connection connection,
            int sequenceYear
    ) throws SQLException {

        String updateSql = """
                INSERT INTO REQUEST_NUMBER_SEQUENCE
                    (Sequence_Year, Last_Number)
                VALUES (?, 1)
                ON DUPLICATE KEY UPDATE
                    Last_Number = Last_Number + 1
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(updateSql)) {

            statement.setInt(
                    1,
                    sequenceYear
            );

            statement.executeUpdate();
        }

        String selectSql = """
                SELECT Last_Number
                FROM REQUEST_NUMBER_SEQUENCE
                WHERE Sequence_Year = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(selectSql)) {

            statement.setInt(
                    1,
                    sequenceYear
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    throw new SQLException(
                            "Unable to retrieve the request sequence number."
                    );
                }

                return result.getLong(
                        "Last_Number"
                );
            }
        }
    }

    private String formatRequestNumber(
            int sequenceYear,
            long sequenceNumber
    ) {
        return String.format(
                Locale.ROOT,
                "SR-%04d-%06d",
                sequenceYear,
                sequenceNumber
        );
    }

    private long insertServiceRequest(
            Connection connection,
            CreateServiceRequestRequest request,
            String requestNumber,
            int requesterId,
            int requesterDepartmentId
    ) throws SQLException {

        String sql = """
                INSERT INTO SERVICE_REQUEST (
                    Request_Number,
                    Requester_ID,
                    Requester_Department_ID,
                    Requested_Category_ID,
                    Preferred_Priority,
                    Request_Title,
                    Request_Description,
                    Request_Location,
                    Date_Reported,
                    Current_Status
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Submitted'
                )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            statement.setString(
                    1,
                    requestNumber
            );

            statement.setInt(
                    2,
                    requesterId
            );

            statement.setInt(
                    3,
                    requesterDepartmentId
            );

            if (request.getRequestedCategoryId() == null) {
                statement.setNull(
                        4,
                        Types.TINYINT
                );
            } else {
                statement.setInt(
                        4,
                        request.getRequestedCategoryId()
                );
            }

            statement.setString(
                    5,
                    request.getPreferredPriority()
            );

            statement.setString(
                    6,
                    request.getTitle()
            );

            statement.setString(
                    7,
                    request.getDescription()
            );

            statement.setString(
                    8,
                    request.getLocation()
            );

            statement.setDate(
                    9,
                    Date.valueOf(
                            request.getDateReported()
                    )
            );

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "The service request insert did not create exactly one row."
                );
            }

            try (ResultSet keys =
                         statement.getGeneratedKeys()) {

                if (!keys.next()) {
                    throw new SQLException(
                            "Unable to obtain the generated Request_ID."
                    );
                }

                return keys.getLong(1);
            }
        }
    }

    private void insertInitialStatusHistory(
            Connection connection,
            long requestId,
            int requesterId,
            int requesterRoleId
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
                    ?, NULL, 'Submitted', ?, ?, ?
                )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    requestId
            );

            statement.setInt(
                    2,
                    requesterId
            );

            statement.setInt(
                    3,
                    requesterRoleId
            );

            statement.setString(
                    4,
                    "Service request submitted by the requester."
            );

            requireOneInsertedRow(
                    statement,
                    "initial request status history"
            );
        }
    }

    private void insertPendingAiRecommendation(
            Connection connection,
            long requestId
    ) throws SQLException {

        String sql = """
                INSERT INTO AI_RECOMMENDATION (
                    Request_ID,
                    Recommendation_Sequence,
                    Is_Current,
                    Analysis_Status,
                    Recommendation_Method,
                    Analysis_Message
                )
                VALUES (
                    ?, 1, TRUE, 'Pending', 'None', ?
                )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    requestId
            );

            statement.setString(
                    2,
                    "Awaiting advisory AI analysis."
            );

            requireOneInsertedRow(
                    statement,
                    "pending AI recommendation"
            );
        }
    }

    private void insertSubmissionNotification(
            Connection connection,
            long requestId,
            String requestNumber,
            int requesterId
    ) throws SQLException {

        String sql = """
                INSERT INTO NOTIFICATION (
                    Recipient_ID,
                    Triggered_By_ID,
                    Request_ID,
                    Notification_Type,
                    Notification_Title,
                    Notification_Message,
                    Action_URL,
                    Notification_Event_Key
                )
                VALUES (
                    ?, ?, ?, 'REQUEST_SUBMITTED', ?, ?, ?, ?
                )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    requesterId
            );

            statement.setInt(
                    2,
                    requesterId
            );

            statement.setLong(
                    3,
                    requestId
            );

            statement.setString(
                    4,
                    "Request submitted"
            );

            statement.setString(
                    5,
                    "Request "
                            + requestNumber
                            + " was submitted successfully."
            );

            statement.setNull(
                    6,
                    Types.VARCHAR
            );

            statement.setString(
                    7,
                    "REQUEST_SUBMITTED:" + requestId
            );

            requireOneInsertedRow(
                    statement,
                    "request submission notification"
            );
        }
    }

    private void insertSubmissionAuditLog(
            Connection connection,
            long requestId,
            String requestNumber,
            CreateServiceRequestRequest request,
            int requesterId,
            int requesterRoleId,
            String clientIpAddress,
            String clientUserAgent
    ) throws SQLException {

        String sql = """
                INSERT INTO AUDIT_LOG (
                    Actor_Type,
                    Actor_ID,
                    Actor_Role_ID,
                    Request_ID,
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
                    'REQUEST_SUBMITTED',
                    'SERVICE_REQUEST',
                    ?,
                    'SUCCESS',
                    ?,
                    JSON_OBJECT(
                        'requestNumber', ?,
                        'preferredPriority', ?,
                        'requestedCategoryId', ?
                    ),
                    ?,
                    ?,
                    ?
                )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    requesterId
            );

            statement.setInt(
                    2,
                    requesterRoleId
            );

            statement.setLong(
                    3,
                    requestId
            );

            statement.setLong(
                    4,
                    requestId
            );

            statement.setString(
                    5,
                    "Requester submitted service request "
                            + requestNumber
                            + "."
            );

            statement.setString(
                    6,
                    requestNumber
            );

            statement.setString(
                    7,
                    request.getPreferredPriority()
            );

            if (request.getRequestedCategoryId() == null) {
                statement.setNull(
                        8,
                        Types.TINYINT
                );
            } else {
                statement.setInt(
                        8,
                        request.getRequestedCategoryId()
                );
            }

            setNullableString(
                    statement,
                    9,
                    limitNullableText(
                            clientIpAddress,
                            45
                    )
            );

            setNullableString(
                    statement,
                    10,
                    limitNullableText(
                            clientUserAgent,
                            500
                    )
            );

            statement.setString(
                    11,
                    UUID.randomUUID().toString()
            );

            requireOneInsertedRow(
                    statement,
                    "request submission audit record"
            );
        }
    }

    private void requireOneInsertedRow(
            PreparedStatement statement,
            String recordDescription
    ) throws SQLException {

        if (statement.executeUpdate() != 1) {
            throw new SQLException(
                    "The "
                            + recordDescription
                            + " insert did not create exactly one row."
            );
        }
    }

    private void setNullableString(
            PreparedStatement statement,
            int parameterIndex,
            String value
    ) throws SQLException {

        if (value == null) {
            statement.setNull(
                    parameterIndex,
                    Types.VARCHAR
            );
        } else {
            statement.setString(
                    parameterIndex,
                    value
            );
        }
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

        if (trimmed.length() <= maximumLength) {
            return trimmed;
        }

        return trimmed.substring(
                0,
                maximumLength
        );
    }

    private ServiceRequest buildCreatedRequest(
            CreateServiceRequestRequest request,
            long requestId,
            String requestNumber,
            int requesterId,
            int requesterDepartmentId
    ) {
        ServiceRequest createdRequest =
                new ServiceRequest();

        createdRequest.setRequestId(
                requestId
        );

        createdRequest.setRequestNumber(
                requestNumber
        );

        createdRequest.setRequesterId(
                requesterId
        );

        createdRequest.setRequesterDepartmentId(
                requesterDepartmentId
        );

        createdRequest.setRequestedCategoryId(
                request.getRequestedCategoryId()
        );

        createdRequest.setPreferredPriority(
                request.getPreferredPriority()
        );

        createdRequest.setTitle(
                request.getTitle()
        );

        createdRequest.setDescription(
                request.getDescription()
        );

        createdRequest.setLocation(
                request.getLocation()
        );

        createdRequest.setDateReported(
                request.getDateReported()
        );

        createdRequest.setCurrentStatus(
                INITIAL_STATUS
        );

        return createdRequest;
    }

    private record RequesterContext(
            int departmentId,
            int roleId
    ) {
    }
}