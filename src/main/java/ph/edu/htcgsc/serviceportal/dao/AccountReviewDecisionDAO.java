package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Set;
import java.util.UUID;

public final class AccountReviewDecisionDAO {

    private static final int SERVICE_ADMINISTRATOR_ROLE_ID = 2;

    private static final Set<Integer> PRIVILEGED_ROLE_IDS =
            Set.of(2, 3, 4);

    public enum DecisionFailureReason {
        REVIEW_NOT_FOUND,
        REVIEW_ALREADY_DECIDED,
        REVIEWER_NOT_AUTHORIZED,
        SELF_REVIEW_NOT_ALLOWED,
        ACCOUNT_STATE_INVALID,
        ACCOUNT_ASSIGNMENT_INVALID
    }

    public static final class DecisionException
            extends Exception {

        private final DecisionFailureReason reason;

        public DecisionException(
                DecisionFailureReason reason,
                String message
        ) {
            super(message);
            this.reason = reason;
        }

        public DecisionFailureReason getReason() {
            return reason;
        }
    }

    public record DecisionResult(
            long accountReviewId,
            int personnelId,
            String reviewStatus,
            String accountStatus,
            String decisionRemarks
    ) {
    }

    public DecisionResult decideReview(
            long accountReviewId,
            String decision,
            String remarks,
            int reviewerPersonnelId,
            String clientIpAddress,
            String clientUserAgent
    ) throws SQLException, DecisionException {

        if (accountReviewId <= 0) {
            throw new IllegalArgumentException(
                    "The account review ID is invalid."
            );
        }

        if (reviewerPersonnelId <= 0) {
            throw new IllegalArgumentException(
                    "The reviewer personnel ID is invalid."
            );
        }

        String normalizedDecision =
                normalizeDecision(decision);

        String normalizedRemarks =
                normalizeRemarks(remarks);

        String accountStatus =
                "Approved".equals(normalizedDecision)
                        ? "Active"
                        : "Inactive";

        try (Connection connection =
                     DatabaseConnection.getConnection()) {

            boolean originalAutoCommit =
                    connection.getAutoCommit();

            connection.setAutoCommit(false);

            try {
                ReviewerContext reviewer =
                        loadAndLockReviewer(
                                connection,
                                reviewerPersonnelId
                        );

                ReviewContext review =
                        loadAndLockReview(
                                connection,
                                accountReviewId
                        );

                if (
                    review.personnelId()
                    == reviewerPersonnelId
                ) {
                    throw new DecisionException(
                            DecisionFailureReason
                                    .SELF_REVIEW_NOT_ALLOWED,
                            "A reviewer cannot decide their own account review."
                    );
                }

                validateReviewState(review);

                updateAccountReview(
                        connection,
                        accountReviewId,
                        normalizedDecision,
                        normalizedRemarks,
                        reviewerPersonnelId
                );

                updatePersonnelAccount(
                        connection,
                        review.personnelId(),
                        accountStatus
                );

                insertDecisionNotification(
                        connection,
                        accountReviewId,
                        review.personnelId(),
                        reviewerPersonnelId,
                        normalizedDecision
                );

                insertDecisionAuditLog(
                        connection,
                        accountReviewId,
                        review,
                        reviewerPersonnelId,
                        reviewer.roleId(),
                        normalizedDecision,
                        clientIpAddress,
                        clientUserAgent
                );

                connection.commit();

                return new DecisionResult(
                        accountReviewId,
                        review.personnelId(),
                        normalizedDecision,
                        accountStatus,
                        normalizedRemarks
                );

            } catch (
                    SQLException
                    | DecisionException
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
                    // Closing the connection is the final safeguard.
                }
            }
        }
    }

    private ReviewerContext loadAndLockReviewer(
            Connection connection,
            int reviewerPersonnelId
    ) throws SQLException, DecisionException {

        String sql = """
                SELECT
                    sp.Account_Status,
                    pra.Role_ID
                FROM SCHOOL_PERSONNEL sp
                INNER JOIN PERSONNEL_ROLE_ASSIGNMENT pra
                    ON pra.Personnel_ID =
                       sp.Personnel_ID
                WHERE sp.Personnel_ID = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    reviewerPersonnelId
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    throw reviewerNotAuthorized();
                }

                String accountStatus =
                        result.getString(
                                "Account_Status"
                        );

                int roleId =
                        result.getInt(
                                "Role_ID"
                        );

                if (result.next()) {
                    throw reviewerNotAuthorized();
                }

                if (
                    accountStatus == null
                    || !"Active".equalsIgnoreCase(
                            accountStatus
                    )
                    || roleId
                        != SERVICE_ADMINISTRATOR_ROLE_ID
                ) {
                    throw reviewerNotAuthorized();
                }

                return new ReviewerContext(roleId);
            }
        }
    }

    private DecisionException reviewerNotAuthorized() {
        return new DecisionException(
                DecisionFailureReason
                        .REVIEWER_NOT_AUTHORIZED,
                "The reviewer is not an active Service Administrator."
        );
    }

    private ReviewContext loadAndLockReview(
            Connection connection,
            long accountReviewId
    ) throws SQLException, DecisionException {

        String sql = """
                SELECT
                    ar.Personnel_ID,
                    ar.Requested_Role_ID,
                    ar.Requested_Department_ID,
                    ar.Review_Status,

                    sp.Account_Status,
                    sp.Department_ID,

                    pra.Role_ID AS Assigned_Role_ID

                FROM ACCOUNT_REVIEW ar

                INNER JOIN SCHOOL_PERSONNEL sp
                    ON sp.Personnel_ID =
                       ar.Personnel_ID

                LEFT JOIN PERSONNEL_ROLE_ASSIGNMENT pra
                    ON pra.Personnel_ID =
                       sp.Personnel_ID

                WHERE ar.Account_Review_ID = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    accountReviewId
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    throw new DecisionException(
                            DecisionFailureReason
                                    .REVIEW_NOT_FOUND,
                            "The account review was not found."
                    );
                }

                Integer assignedRoleId =
                        getNullableInteger(
                                result,
                                "Assigned_Role_ID"
                        );

                ReviewContext review =
                        new ReviewContext(
                                result.getInt(
                                        "Personnel_ID"
                                ),
                                result.getInt(
                                        "Requested_Role_ID"
                                ),
                                result.getInt(
                                        "Requested_Department_ID"
                                ),
                                result.getString(
                                        "Review_Status"
                                ),
                                result.getString(
                                        "Account_Status"
                                ),
                                result.getInt(
                                        "Department_ID"
                                ),
                                assignedRoleId
                        );

                if (result.next()) {
                    throw new DecisionException(
                            DecisionFailureReason
                                    .ACCOUNT_ASSIGNMENT_INVALID,
                            "The applicant has multiple role assignments."
                    );
                }

                return review;
            }
        }
    }

    private void validateReviewState(
            ReviewContext review
    ) throws DecisionException {

        if (
            review.reviewStatus() == null
            || !"Pending".equalsIgnoreCase(
                    review.reviewStatus()
            )
        ) {
            throw new DecisionException(
                    DecisionFailureReason
                            .REVIEW_ALREADY_DECIDED,
                    "The account review has already been decided."
            );
        }

        if (
            review.accountStatus() == null
            || !("Pending".equalsIgnoreCase(review.accountStatus())
                 || "Pending Approval".equalsIgnoreCase(review.accountStatus()))
        ) {
            throw new DecisionException(
                    DecisionFailureReason
                            .ACCOUNT_STATE_INVALID,
                    "The applicant account is not pending."
            );
        }

        if (
            !PRIVILEGED_ROLE_IDS.contains(
                    review.requestedRoleId()
            )
            || review.assignedRoleId() == null
            || review.assignedRoleId()
                != review.requestedRoleId()
            || review.accountDepartmentId()
                != review.requestedDepartmentId()
        ) {
            throw new DecisionException(
                    DecisionFailureReason
                            .ACCOUNT_ASSIGNMENT_INVALID,
                    "The applicant role or department assignment is inconsistent."
            );
        }
    }

    private void updateAccountReview(
            Connection connection,
            long accountReviewId,
            String decision,
            String remarks,
            int reviewerPersonnelId
    ) throws SQLException, DecisionException {

        String sql = """
                UPDATE ACCOUNT_REVIEW
                SET
                    Review_Status = ?,
                    Reviewed_By_ID = ?,
                    Decision_Remarks = ?,
                    Reviewed_At = CURRENT_TIMESTAMP
                WHERE Account_Review_ID = ?
                  AND Review_Status = 'Pending'
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    decision
            );

            statement.setInt(
                    2,
                    reviewerPersonnelId
            );

            statement.setString(
                    3,
                    remarks
            );

            statement.setLong(
                    4,
                    accountReviewId
            );

            if (statement.executeUpdate() != 1) {
                throw new DecisionException(
                        DecisionFailureReason
                                .REVIEW_ALREADY_DECIDED,
                        "The account review is no longer pending."
                );
            }
        }
    }

    private void updatePersonnelAccount(
            Connection connection,
            int personnelId,
            String accountStatus
    ) throws SQLException, DecisionException {

        String sql = """
                UPDATE SCHOOL_PERSONNEL
                SET Account_Status = ?
                WHERE Personnel_ID = ?
                  AND Account_Status IN ('Pending', 'Pending Approval')
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    accountStatus
            );

            statement.setInt(
                    2,
                    personnelId
            );

            if (statement.executeUpdate() != 1) {
                throw new DecisionException(
                        DecisionFailureReason
                                .ACCOUNT_STATE_INVALID,
                        "The applicant account is no longer pending."
                );
            }
        }
    }

    private void insertDecisionNotification(
            Connection connection,
            long accountReviewId,
            int applicantPersonnelId,
            int reviewerPersonnelId,
            String decision
    ) throws SQLException {

        boolean approved =
                "Approved".equals(decision);

        String notificationType =
                approved
                        ? "ACCOUNT_APPROVED"
                        : "ACCOUNT_REJECTED";

        String title =
                approved
                        ? "Account approved"
                        : "Account registration rejected";

        String message =
                approved
                        ? "Your HTC Service Portal account was approved and is now active."
                        : "Your HTC Service Portal account registration was rejected. Contact an authorized school administrator for assistance.";

        String eventKey =
                "ACCOUNT_REVIEW_DECISION:"
                        + accountReviewId;

        String sql = """
                INSERT INTO NOTIFICATION (
                    Recipient_ID,
                    Triggered_By_ID,
                    Request_ID,
                    Work_Order_ID,
                    Notification_Type,
                    Notification_Title,
                    Notification_Message,
                    Action_URL,
                    Notification_Event_Key
                )
                VALUES (
                    ?, ?, NULL, NULL, ?, ?, ?, NULL, ?
                )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    applicantPersonnelId
            );

            statement.setInt(
                    2,
                    reviewerPersonnelId
            );

            statement.setString(
                    3,
                    notificationType
            );

            statement.setString(
                    4,
                    title
            );

            statement.setString(
                    5,
                    message
            );

            statement.setString(
                    6,
                    eventKey
            );

            requireOneInsertedRow(
                    statement,
                    "account decision notification"
            );
        }
    }

    private void insertDecisionAuditLog(
            Connection connection,
            long accountReviewId,
            ReviewContext review,
            int reviewerPersonnelId,
            int reviewerRoleId,
            String decision,
            String clientIpAddress,
            String clientUserAgent
    ) throws SQLException {

        String actionType =
                "Approved".equals(decision)
                        ? "ACCOUNT_REVIEW_APPROVED"
                        : "ACCOUNT_REVIEW_REJECTED";

        String summary =
                "Service Administrator "
                        + decision.toLowerCase()
                        + " account review "
                        + accountReviewId
                        + " for personnel "
                        + review.personnelId()
                        + ".";

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
                    NULL,
                    NULL,
                    ?,
                    'ACCOUNT_REVIEW',
                    ?,
                    'SUCCESS',
                    ?,
                    JSON_OBJECT(
                        'accountReviewId', ?,
                        'personnelId', ?,
                        'requestedRoleId', ?,
                        'requestedDepartmentId', ?,
                        'decision', ?
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
                    reviewerPersonnelId
            );

            statement.setInt(
                    2,
                    reviewerRoleId
            );

            statement.setString(
                    3,
                    actionType
            );

            statement.setLong(
                    4,
                    accountReviewId
            );

            statement.setString(
                    5,
                    summary
            );

            statement.setLong(
                    6,
                    accountReviewId
            );

            statement.setInt(
                    7,
                    review.personnelId()
            );

            statement.setInt(
                    8,
                    review.requestedRoleId()
            );

            statement.setInt(
                    9,
                    review.requestedDepartmentId()
            );

            statement.setString(
                    10,
                    decision
            );

            setNullableString(
                    statement,
                    11,
                    limitNullableText(
                            clientIpAddress,
                            45
                    )
            );

            setNullableString(
                    statement,
                    12,
                    limitNullableText(
                            clientUserAgent,
                            500
                    )
            );

            statement.setString(
                    13,
                    UUID.randomUUID().toString()
            );

            requireOneInsertedRow(
                    statement,
                    "account decision audit record"
            );
        }
    }

    private String normalizeDecision(
            String decision
    ) {
        if (decision == null) {
            throw new IllegalArgumentException(
                    "The decision is required."
            );
        }

        String trimmed =
                decision.trim();

        if ("Approved".equalsIgnoreCase(trimmed)) {
            return "Approved";
        }

        if ("Rejected".equalsIgnoreCase(trimmed)) {
            return "Rejected";
        }

        throw new IllegalArgumentException(
                "The decision must be Approved or Rejected."
        );
    }

    private String normalizeRemarks(
            String remarks
    ) {
        if (remarks == null) {
            throw new IllegalArgumentException(
                    "Decision remarks are required."
            );
        }

        String normalized =
                remarks
                        .trim()
                        .replaceAll("\\s+", " ");

        if (
            normalized.length() < 5
            || normalized.length() > 1000
        ) {
            throw new IllegalArgumentException(
                    "Decision remarks must contain 5 to 1000 characters."
            );
        }

        return normalized;
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

    private record ReviewerContext(
            int roleId
    ) {
    }

    private record ReviewContext(
            int personnelId,
            int requestedRoleId,
            int requestedDepartmentId,
            String reviewStatus,
            String accountStatus,
            int accountDepartmentId,
            Integer assignedRoleId
    ) {
    }
}