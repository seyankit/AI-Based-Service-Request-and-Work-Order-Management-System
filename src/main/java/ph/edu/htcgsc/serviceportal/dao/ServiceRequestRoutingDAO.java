package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

public final class ServiceRequestRoutingDAO {

    private static final int SERVICE_ADMINISTRATOR_ROLE_ID = 2;

    public record ForwardResult(
            long requestId,
            String requestNumber,
            String previousStatus,
            String newStatus,
            int finalCategoryId,
            String finalPriority,
            int routedDepartmentId,
            long approvalId,
            int approvalSequence
    ) {
    }

    private record RequestState(
            long requestId,
            String requestNumber,
            String currentStatus
    ) {
    }

    public ForwardResult forwardForApproval(
            int actorPersonnelId,
            int actorRoleId,
            long requestId,
            int finalCategoryId,
            String finalPriority,
            int routedDepartmentId,
            String clientIpAddress,
            String clientUserAgent
    ) throws SQLException {

        if (actorPersonnelId <= 0) {
            throw new SecurityException(
                    "Authentication is required."
            );
        }

        if (requestId <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid service request."
            );
        }

        if (finalCategoryId <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid final service category."
            );
        }

        if (routedDepartmentId <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid routed department."
            );
        }

        String normalizedPriority =
                normalizePriority(
                        finalPriority
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

                requireActiveCategory(
                        connection,
                        finalCategoryId
                );

                requireDepartmentExists(
                        connection,
                        routedDepartmentId
                );

                RequestState requestState =
                        loadAndLockRequest(
                                connection,
                                requestId
                        );

                if (
                    !"Submitted".equalsIgnoreCase(
                            requestState.currentStatus()
                    )
                ) {
                    throw new IllegalStateException(
                            "Only Submitted requests can be forwarded for approval."
                    );
                }

                requireNoCurrentApproval(
                        connection,
                        requestId
                );

                int approvalSequence =
                        nextApprovalSequence(
                                connection,
                                requestId
                        );

                updateServiceRequest(
                        connection,
                        requestId,
                        finalCategoryId,
                        normalizedPriority,
                        routedDepartmentId
                );

                long approvalId =
                        insertPendingApproval(
                                connection,
                                requestId,
                                approvalSequence,
                                routedDepartmentId,
                                actorPersonnelId
                        );

                insertStatusHistory(
                        connection,
                        requestId,
                        requestState.currentStatus(),
                        actorPersonnelId,
                        actorRoleId,
                        finalCategoryId,
                        normalizedPriority,
                        routedDepartmentId
                );

                insertAuditLog(
                        connection,
                        actorPersonnelId,
                        actorRoleId,
                        requestState,
                        finalCategoryId,
                        normalizedPriority,
                        routedDepartmentId,
                        approvalId,
                        approvalSequence,
                        clientIpAddress,
                        clientUserAgent
                );

                connection.commit();

                return new ForwardResult(
                        requestId,
                        requestState.requestNumber(),
                        requestState.currentStatus(),
                        "Awaiting Approval",
                        finalCategoryId,
                        normalizedPriority,
                        routedDepartmentId,
                        approvalId,
                        approvalSequence
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

    private void requireActiveCategory(
            Connection connection,
            int categoryId
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM SERVICE_CATEGORY
                WHERE Category_ID = ?
                  AND Is_Active = TRUE
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    categoryId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "The selected service category does not exist or is inactive."
                    );
                }
            }
        }
    }

    private void requireDepartmentExists(
            Connection connection,
            int departmentId
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM DEPARTMENT
                WHERE Department_ID = ?
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    departmentId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "The selected routed department does not exist."
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

    private void requireNoCurrentApproval(
            Connection connection,
            long requestId
    ) throws SQLException {

        String sql = """
                SELECT
                    Approval_ID
                FROM REQUEST_APPROVAL
                WHERE Request_ID = ?
                  AND Is_Current = TRUE
                LIMIT 1
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
                if (resultSet.next()) {
                    throw new IllegalStateException(
                            "This request already has a current approval workflow."
                    );
                }
            }
        }
    }

    private int nextApprovalSequence(
            Connection connection,
            long requestId
    ) throws SQLException {

        String sql = """
                SELECT
                    COALESCE(
                        MAX(Approval_Sequence),
                        0
                    ) + 1 AS Next_Sequence
                FROM REQUEST_APPROVAL
                WHERE Request_ID = ?
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
                    throw new SQLException(
                            "Unable to calculate the next approval sequence."
                    );
                }

                return resultSet.getInt(
                        "Next_Sequence"
                );
            }
        }
    }

    private void updateServiceRequest(
            Connection connection,
            long requestId,
            int finalCategoryId,
            String finalPriority,
            int routedDepartmentId
    ) throws SQLException {

        String sql = """
                UPDATE SERVICE_REQUEST
                SET
                    Final_Category_ID = ?,
                    Final_Priority = ?,
                    Routed_Department_ID = ?,
                    Current_Status = 'Awaiting Approval'
                WHERE Request_ID = ?
                  AND Current_Status = 'Submitted'
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    finalCategoryId
            );

            statement.setString(
                    2,
                    finalPriority
            );

            statement.setInt(
                    3,
                    routedDepartmentId
            );

            statement.setLong(
                    4,
                    requestId
            );

            int rowsUpdated =
                    statement.executeUpdate();

            if (rowsUpdated != 1) {
                throw new IllegalStateException(
                        "The request is no longer available for forwarding."
                );
            }
        }
    }

    private long insertPendingApproval(
            Connection connection,
            long requestId,
            int approvalSequence,
            int departmentId,
            int requestedBy
    ) throws SQLException {

        String sql = """
                INSERT INTO REQUEST_APPROVAL (
                    Request_ID,
                    Approval_Sequence,
                    Is_Current,
                    Department_ID,
                    Requested_By,
                    Decision
                )
                VALUES (
                    ?,
                    ?,
                    TRUE,
                    ?,
                    ?,
                    'Pending'
                )
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
                    requestId
            );

            statement.setInt(
                    2,
                    approvalSequence
            );

            statement.setInt(
                    3,
                    departmentId
            );

            statement.setInt(
                    4,
                    requestedBy
            );

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "The approval request could not be created."
                );
            }

            try (
                ResultSet generatedKeys =
                        statement.getGeneratedKeys()
            ) {
                if (!generatedKeys.next()) {
                    throw new SQLException(
                            "The approval identifier was not returned."
                    );
                }

                return generatedKeys.getLong(1);
            }
        }
    }

    private void insertStatusHistory(
            Connection connection,
            long requestId,
            String previousStatus,
            int actorPersonnelId,
            int actorRoleId,
            int finalCategoryId,
            String finalPriority,
            int routedDepartmentId
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
                    'Awaiting Approval',
                    ?,
                    ?,
                    ?
                )
                """;

        String reason =
                "Service Administrator reviewed and routed the request for department approval. "
                + "Final category ID: "
                + finalCategoryId
                + ", final priority: "
                + finalPriority
                + ", routed department ID: "
                + routedDepartmentId
                + ".";

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setLong(
                    1,
                    requestId
            );

            statement.setString(
                    2,
                    previousStatus
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
                        "The request status history could not be recorded."
                );
            }
        }
    }

    private void insertAuditLog(
            Connection connection,
            int actorPersonnelId,
            int actorRoleId,
            RequestState requestState,
            int finalCategoryId,
            String finalPriority,
            int routedDepartmentId,
            long approvalId,
            int approvalSequence,
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
                    'SERVICE_REQUEST_FORWARDED',
                    'SERVICE_REQUEST',
                    ?,
                    'SUCCESS',
                    ?,
                    JSON_OBJECT(
                        'requestNumber', ?,
                        'previousStatus', ?,
                        'newStatus', 'Awaiting Approval',
                        'finalCategoryId', ?,
                        'finalPriority', ?,
                        'routedDepartmentId', ?,
                        'approvalId', ?,
                        'approvalSequence', ?
                    ),
                    ?,
                    ?,
                    ?
                )
                """;

        String summary =
                "Service Administrator forwarded "
                + requestState.requestNumber()
                + " for department approval.";

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    actorPersonnelId
            );

            statement.setInt(
                    2,
                    actorRoleId
            );

            statement.setLong(
                    3,
                    requestState.requestId()
            );

            statement.setLong(
                    4,
                    requestState.requestId()
            );

            statement.setString(
                    5,
                    summary
            );

            statement.setString(
                    6,
                    requestState.requestNumber()
            );

            statement.setString(
                    7,
                    requestState.currentStatus()
            );

            statement.setInt(
                    8,
                    finalCategoryId
            );

            statement.setString(
                    9,
                    finalPriority
            );

            statement.setInt(
                    10,
                    routedDepartmentId
            );

            statement.setLong(
                    11,
                    approvalId
            );

            statement.setInt(
                    12,
                    approvalSequence
            );

            statement.setString(
                    13,
                    limitNullableText(
                            clientIpAddress,
                            45
                    )
            );

            statement.setString(
                    14,
                    limitNullableText(
                            clientUserAgent,
                            500
                    )
            );

            statement.setString(
                    15,
                    UUID.randomUUID()
                            .toString()
            );

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "The forwarding audit record could not be written."
                );
            }
        }
    }

    private String normalizePriority(
            String priority
    ) {

        if (
            priority == null
            || priority.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Select a final priority."
            );
        }

        return switch (
            priority.trim()
                    .toLowerCase(
                            Locale.ROOT
                    )
        ) {
            case "low" ->
                    "Low";

            case "medium" ->
                    "Medium";

            case "high" ->
                    "High";

            case "urgent" ->
                    "Urgent";

            default ->
                    throw new IllegalArgumentException(
                            "Final priority must be Low, Medium, High, or Urgent."
                    );
        };
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