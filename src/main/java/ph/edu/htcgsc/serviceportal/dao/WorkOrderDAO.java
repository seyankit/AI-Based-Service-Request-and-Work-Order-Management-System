package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.util.WorkflowPolicy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.activeActor;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.audit;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.query;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.workHistory;

/** Administrator work-order creation and read access for Phase 4A. */
public final class WorkOrderDAO {
    private static final int ADMINISTRATOR_ROLE_ID = 2;
    private static final ZoneId PORTAL_TIME_ZONE = ZoneId.of("Asia/Manila");

    public enum CreationFailure {
        REQUEST_NOT_FOUND,
        REQUEST_NOT_APPROVED,
        DUPLICATE_WORK_ORDER,
        REQUEST_NOT_ROUTED
    }

    public static final class CreationException extends Exception {
        private final CreationFailure failure;

        public CreationException(CreationFailure failure, String message) {
            super(message);
            this.failure = failure;
        }

        public CreationFailure failure() {
            return failure;
        }
    }

    public record CreateInput(
            Long requestId,
            String requestNumber,
            String workDescription,
            LocalDate targetCompletionDate
    ) { }

    public List<Map<String, Object>> list(
            int actor,
            int role,
            Long workOrderId
    ) throws SQLException {
        requireAdministrator(role);
        try (Connection connection = DatabaseConnection.getConnection()) {
            activeActor(connection, actor, role);
            String filter = workOrderId == null ? "" : "WHERE wo.Work_Order_ID = ?";
            String limit = workOrderId == null ? "LIMIT 250" : "LIMIT 1";
            String sql = """
                    SELECT
                        wo.Work_Order_ID AS workOrderId,
                        wo.Work_Order_Number AS workOrderNumber,
                        wo.Request_ID AS requestId,
                        sr.Request_Number AS requestNumber,
                        sr.Request_Title AS requestTitle,
                        sr.Request_Description AS requestDescription,
                        sr.Request_Location AS requestLocation,
                        COALESCE(final_category.Category_Name, requested_category.Category_Name) AS category,
                        wo.Department_ID AS departmentId,
                        department.Department_Name AS departmentName,
                        wo.Created_By AS createdBy,
                        wo.Work_Description AS workDescription,
                        wo.Work_Status AS status,
                        wo.Target_Start_Date AS targetStartDate,
                        wo.Target_Completion_Date AS targetCompletionDate,
                        wo.Acknowledged_At AS acknowledgedAt,
                        wo.Actual_Start_At AS actualStartAt,
                        wo.Completion_Summary AS completionSummary,
                        wo.Completed_At AS completedAt,
                        wo.Verified_At AS verifiedAt,
                        wo.Created_At AS createdAt,
                        wo.Updated_At AS updatedAt
                    FROM WORK_ORDER wo
                    JOIN SERVICE_REQUEST sr ON sr.Request_ID = wo.Request_ID
                    JOIN DEPARTMENT department ON department.Department_ID = wo.Department_ID
                    LEFT JOIN SERVICE_CATEGORY requested_category
                        ON requested_category.Category_ID = sr.Requested_Category_ID
                    LEFT JOIN SERVICE_CATEGORY final_category
                        ON final_category.Category_ID = sr.Final_Category_ID
                    %s
                    ORDER BY wo.Created_At DESC, wo.Work_Order_ID DESC
                    %s
                    """.formatted(filter, limit);
            return workOrderId == null
                    ? query(connection, sql)
                    : query(connection, sql, workOrderId);
        }
    }

    public Map<String, Object> create(
            int actor,
            int role,
            CreateInput input
    ) throws SQLException, CreationException {
        requireAdministrator(role);
        if (input == null) throw new IllegalArgumentException("Work-order input is required.");
        String description = WorkflowPolicy.text(input.workDescription(), "Work description", 10, 2000);
        if (input.requestId() != null && input.requestId() <= 0) {
            throw new IllegalArgumentException("The request ID must be positive.");
        }
        String requestNumber = input.requestNumber() == null ? "" : input.requestNumber().trim();
        if (input.requestId() == null && requestNumber.isEmpty()) {
            throw new IllegalArgumentException("A service request is required.");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                activeActor(connection, actor, role);
                Map<String, Object> request = lockRequest(connection, input.requestId(), requestNumber);
                if (request == null) {
                    throw new CreationException(CreationFailure.REQUEST_NOT_FOUND,
                            "The requested service request was not found.");
                }
                if (!"Approved".equals(request.get("status"))) {
                    throw new CreationException(CreationFailure.REQUEST_NOT_APPROVED,
                            "Only Approved service requests can receive a work order.");
                }
                long requestId = number(request, "requestId");
                if (existingWorkOrder(connection, requestId)) {
                    throw new CreationException(CreationFailure.DUPLICATE_WORK_ORDER,
                            "A work order already exists for this service request.");
                }
                Integer departmentId = nullableInteger(request, "departmentId");
                if (departmentId == null || departmentId <= 0) {
                    throw new CreationException(CreationFailure.REQUEST_NOT_ROUTED,
                            "The approved service request has no routed department.");
                }

                int year = LocalDate.now(PORTAL_TIME_ZONE).getYear();
                long sequence = obtainNextWorkOrderNumber(connection, year);
                String workOrderNumber = formatWorkOrderNumber(year, sequence);
                long workOrderId = insertWorkOrder(connection, workOrderNumber, requestId, departmentId,
                        actor, description, input.targetCompletionDate());
                String remarks = "Work order created for " + string(request, "requestNumber") + ".";
                workHistory(connection, workOrderId, null, "Created", "Created", actor, remarks);
                audit(connection, actor, role, requestId, workOrderId, "WORK_ORDER_CREATED",
                        "Work order " + workOrderNumber + " created.", remarks);
                Map<String, Object> created = query(connection,
                        "SELECT wo.Work_Order_ID AS workOrderId, wo.Work_Order_Number AS workOrderNumber, "
                                + "wo.Request_ID AS requestId, sr.Request_Number AS requestNumber, "
                                + "sr.Request_Title AS requestTitle, wo.Department_ID AS departmentId, "
                                + "department.Department_Name AS departmentName, wo.Work_Description AS workDescription, "
                                + "wo.Work_Status AS status, wo.Target_Completion_Date AS targetCompletionDate, "
                                + "wo.Created_At AS createdAt, wo.Updated_At AS updatedAt "
                                + "FROM WORK_ORDER wo JOIN SERVICE_REQUEST sr ON sr.Request_ID=wo.Request_ID "
                                + "JOIN DEPARTMENT department ON department.Department_ID=wo.Department_ID "
                                + "WHERE wo.Work_Order_ID=?", workOrderId).get(0);
                connection.commit();
                return created;
            } catch (CreationException | RuntimeException exception) {
                rollback(connection);
                throw exception;
            } catch (SQLIntegrityConstraintViolationException exception) {
                rollback(connection);
                if (isRequestDuplicate(exception)) {
                    throw new CreationException(CreationFailure.DUPLICATE_WORK_ORDER,
                            "A work order already exists for this service request.");
                }
                throw exception;
            } catch (SQLException exception) {
                rollback(connection);
                throw exception;
            }
        }
    }

    private Map<String, Object> lockRequest(Connection connection, Long requestId, String requestNumber)
            throws SQLException {
        String predicate = requestId == null ? "sr.Request_Number = ?" : "sr.Request_ID = ?";
        String sql = "SELECT sr.Request_ID AS requestId, sr.Request_Number AS requestNumber, "
                + "sr.Current_Status AS status, sr.Routed_Department_ID AS departmentId "
                + "FROM SERVICE_REQUEST sr WHERE " + predicate + " FOR UPDATE";
        List<Map<String, Object>> rows = requestId == null
                ? query(connection, sql, requestNumber)
                : query(connection, sql, requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean existingWorkOrder(Connection connection, long requestId) throws SQLException {
        return !query(connection, "SELECT Work_Order_ID AS workOrderId FROM WORK_ORDER "
                + "WHERE Request_ID=? FOR UPDATE", requestId).isEmpty();
    }

    private long insertWorkOrder(Connection connection, String number, long requestId, int departmentId,
                                 int actor, String description, LocalDate targetCompletionDate)
            throws SQLException {
        String sql = "INSERT INTO WORK_ORDER (Work_Order_Number, Request_ID, Department_ID, Created_By, "
                + "Work_Description, Work_Status, Target_Completion_Date) "
                + "VALUES (?, ?, ?, ?, ?, 'Created', ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, number);
            statement.setLong(2, requestId);
            statement.setInt(3, departmentId);
            statement.setInt(4, actor);
            statement.setString(5, description);
            statement.setObject(6, targetCompletionDate);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("The work order could not be created.");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("The work-order identifier was not returned.");
                return keys.getLong(1);
            }
        }
    }

    private long obtainNextWorkOrderNumber(Connection connection, int year) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO WORK_ORDER_NUMBER_SEQUENCE (Sequence_Year, Last_Number) VALUES (?, 1) "
                        + "ON DUPLICATE KEY UPDATE Last_Number=Last_Number+1")) {
            statement.setInt(1, year);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT Last_Number FROM WORK_ORDER_NUMBER_SEQUENCE WHERE Sequence_Year=? FOR UPDATE")) {
            statement.setInt(1, year);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Unable to retrieve the work-order sequence number.");
                return result.getLong(1);
            }
        }
    }

    private String formatWorkOrderNumber(int year, long sequence) {
        return String.format(Locale.ROOT, "WO-%04d-%04d", year, sequence);
    }

    private void requireAdministrator(int role) {
        if (role != ADMINISTRATOR_ROLE_ID) {
            throw new SecurityException("Service Administrator access is required.");
        }
    }

    private boolean isRequestDuplicate(SQLIntegrityConstraintViolationException exception) {
        String message = exception.getMessage();
        return message != null && message.toUpperCase(Locale.ROOT).contains("REQUEST");
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original failure.
        }
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static Integer nullableInteger(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).intValue();
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }
}
