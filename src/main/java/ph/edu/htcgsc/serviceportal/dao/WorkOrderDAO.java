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
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.insert;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.query;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.update;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.workHistory;

/** Administrator work-order creation, assignment, and read access. */
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

    public enum AssignmentFailure {
        WORK_ORDER_NOT_FOUND,
        TECHNICIAN_NOT_FOUND,
        WORK_ORDER_NOT_CREATED,
        ALREADY_ASSIGNED,
        TECHNICIAN_NOT_ELIGIBLE,
        TECHNICIAN_DEPARTMENT_MISMATCH
    }

    public static final class AssignmentException extends Exception {
        private final AssignmentFailure failure;

        public AssignmentException(AssignmentFailure failure, String message) {
            super(message);
            this.failure = failure;
        }

        public AssignmentFailure failure() {
            return failure;
        }
    }

    public record AssignmentInput(
            long workOrderId,
            int technicianId,
            String assignmentNotes
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
                        wo.Updated_At AS updatedAt,
                        assignment.Assignment_ID AS assignmentId,
                        assignment.Assignment_Sequence AS assignmentSequence,
                        assignment.Technician_ID AS technicianId,
                        CONCAT(technician.First_Name, ' ', technician.Last_Name) AS technicianName,
                        technician.Email AS technicianEmail,
                        assignment.Assigned_At AS assignedAt,
                        assignment.Acknowledged_At AS assignmentAcknowledgedAt,
                        assignment.Assignment_Notes AS assignmentNotes
                    FROM WORK_ORDER wo
                    JOIN SERVICE_REQUEST sr ON sr.Request_ID = wo.Request_ID
                    JOIN DEPARTMENT department ON department.Department_ID = wo.Department_ID
                    LEFT JOIN SERVICE_CATEGORY requested_category
                        ON requested_category.Category_ID = sr.Requested_Category_ID
                    LEFT JOIN SERVICE_CATEGORY final_category
                        ON final_category.Category_ID = sr.Final_Category_ID
                    LEFT JOIN WORK_ORDER_ASSIGNMENT assignment
                        ON assignment.Work_Order_ID = wo.Work_Order_ID
                        AND assignment.Is_Current = TRUE
                        AND assignment.Assignment_ID = (
                            SELECT MAX(current_assignment.Assignment_ID)
                            FROM WORK_ORDER_ASSIGNMENT current_assignment
                            WHERE current_assignment.Work_Order_ID = wo.Work_Order_ID
                              AND current_assignment.Is_Current = TRUE
                        )
                    LEFT JOIN SCHOOL_PERSONNEL technician
                        ON technician.Personnel_ID = assignment.Technician_ID
                    %s
                    ORDER BY wo.Created_At DESC, wo.Work_Order_ID DESC
                    %s
                    """.formatted(filter, limit);
            return workOrderId == null
                    ? query(connection, sql)
                    : query(connection, sql, workOrderId);
        }
    }

    public Map<String, Object> assign(
            int actor,
            int role,
            AssignmentInput input
    ) throws SQLException, AssignmentException {
        requireAdministrator(role);
        if (input == null) throw new IllegalArgumentException("Assignment input is required.");
        if (input.workOrderId() <= 0) throw new IllegalArgumentException("The work-order ID must be positive.");
        if (input.technicianId() <= 0) throw new IllegalArgumentException("The technician ID must be positive.");
        String notes = input.assignmentNotes() == null || input.assignmentNotes().isBlank()
                ? null
                : WorkflowPolicy.text(input.assignmentNotes(), "Assignment notes", 3, 1000);

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int actorDepartment = activeActor(connection, actor, role);
                Map<String, Object> workOrder = lockWorkOrder(connection, input.workOrderId());
                if (workOrder == null) {
                    throw new AssignmentException(AssignmentFailure.WORK_ORDER_NOT_FOUND,
                            "The requested work order was not found.");
                }
                String currentStatus = string(workOrder, "status");
                try {
                    WorkflowPolicy.assignment(currentStatus);
                } catch (IllegalStateException exception) {
                    throw new AssignmentException(AssignmentFailure.WORK_ORDER_NOT_CREATED,
                            exception.getMessage());
                }

                Map<String, Object> technician = lockTechnician(connection, input.technicianId());
                if (technician == null) {
                    throw new AssignmentException(AssignmentFailure.TECHNICIAN_NOT_FOUND,
                            "The selected technician was not found.");
                }
                if (number(technician, "roleId") != 4
                        || !"Active".equals(string(technician, "accountStatus"))) {
                    throw new AssignmentException(AssignmentFailure.TECHNICIAN_NOT_ELIGIBLE,
                            "The selected technician is not an active service technician.");
                }
                if (number(technician, "departmentId") != number(workOrder, "departmentId")) {
                    throw new AssignmentException(AssignmentFailure.TECHNICIAN_DEPARTMENT_MISMATCH,
                            "The selected technician must belong to the work order department.");
                }
                if (actorDepartment <= 0) {
                    throw new SecurityException("Your account no longer has permission for this action.");
                }
                if (!query(connection,
                        "SELECT Assignment_ID AS assignmentId FROM WORK_ORDER_ASSIGNMENT "
                                + "WHERE Work_Order_ID=? AND Is_Current=TRUE FOR UPDATE",
                        input.workOrderId()).isEmpty()) {
                    throw new AssignmentException(AssignmentFailure.ALREADY_ASSIGNED,
                            "This work order already has a current technician assignment.");
                }

                long assignmentId = insert(connection,
                        "INSERT INTO WORK_ORDER_ASSIGNMENT "
                                + "(Work_Order_ID,Assignment_Sequence,Technician_ID,Assigned_By,Assignment_Notes,Is_Current) "
                                + "VALUES(?,1,?,?,?,TRUE)",
                        input.workOrderId(), input.technicianId(), actor, notes);
                if (update(connection,
                        "UPDATE WORK_ORDER SET Work_Status='Assigned' "
                                + "WHERE Work_Order_ID=? AND Work_Status='Created'",
                        input.workOrderId()) != 1) {
                    throw new IllegalStateException("The work order changed. Refresh and try again.");
                }
                String remarks = "Initial technician assignment recorded.";
                long requestId = number(workOrder, "requestId");
                workHistory(connection, input.workOrderId(), "Created", "Assigned", "Assigned", actor, remarks);
                audit(connection, actor, role, requestId, input.workOrderId(), "WORK_ORDER_ASSIGNED",
                        "Work order " + string(workOrder, "workOrderNumber") + " assigned.", remarks);
                WorkflowStore.notify(connection, input.technicianId(), actor, requestId, input.workOrderId(),
                        "TECHNICIAN_ASSIGNED", "Work order assigned",
                        "Work order " + string(workOrder, "workOrderNumber") + " has been assigned to you.",
                        "WORK_ORDER_ASSIGNMENT:" + assignmentId);

                Map<String, Object> assigned = loadWorkOrder(connection, input.workOrderId());
                connection.commit();
                return assigned;
            } catch (AssignmentException | RuntimeException exception) {
                rollback(connection);
                throw exception;
            } catch (SQLException exception) {
                rollback(connection);
                throw exception;
            }
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

    private Map<String, Object> lockWorkOrder(Connection connection, long workOrderId) throws SQLException {
        List<Map<String, Object>> rows = query(connection,
                "SELECT wo.Work_Order_ID AS workOrderId, wo.Work_Order_Number AS workOrderNumber, "
                        + "wo.Request_ID AS requestId, wo.Department_ID AS departmentId, "
                        + "wo.Work_Status AS status FROM WORK_ORDER wo WHERE wo.Work_Order_ID=? FOR UPDATE",
                workOrderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> lockTechnician(Connection connection, int technicianId) throws SQLException {
        List<Map<String, Object>> rows = query(connection,
                "SELECT sp.Personnel_ID AS technicianId, sp.Department_ID AS departmentId, "
                        + "sp.Account_Status AS accountStatus, role.Role_ID AS roleId "
                        + "FROM SCHOOL_PERSONNEL sp LEFT JOIN PERSONNEL_ROLE_ASSIGNMENT role "
                        + "ON role.Personnel_ID=sp.Personnel_ID WHERE sp.Personnel_ID=? FOR UPDATE",
                technicianId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> loadWorkOrder(Connection connection, long workOrderId) throws SQLException {
        return query(connection, """
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
                    wo.Updated_At AS updatedAt,
                    assignment.Assignment_ID AS assignmentId,
                    assignment.Assignment_Sequence AS assignmentSequence,
                    assignment.Technician_ID AS technicianId,
                    CONCAT(technician.First_Name, ' ', technician.Last_Name) AS technicianName,
                    technician.Email AS technicianEmail,
                    assignment.Assigned_At AS assignedAt,
                    assignment.Acknowledged_At AS assignmentAcknowledgedAt,
                    assignment.Assignment_Notes AS assignmentNotes
                FROM WORK_ORDER wo
                JOIN SERVICE_REQUEST sr ON sr.Request_ID = wo.Request_ID
                JOIN DEPARTMENT department ON department.Department_ID = wo.Department_ID
                LEFT JOIN SERVICE_CATEGORY requested_category
                    ON requested_category.Category_ID = sr.Requested_Category_ID
                LEFT JOIN SERVICE_CATEGORY final_category
                    ON final_category.Category_ID = sr.Final_Category_ID
                LEFT JOIN WORK_ORDER_ASSIGNMENT assignment
                    ON assignment.Work_Order_ID = wo.Work_Order_ID
                    AND assignment.Is_Current = TRUE
                    AND assignment.Assignment_ID = (
                        SELECT MAX(current_assignment.Assignment_ID)
                        FROM WORK_ORDER_ASSIGNMENT current_assignment
                        WHERE current_assignment.Work_Order_ID = wo.Work_Order_ID
                          AND current_assignment.Is_Current = TRUE
                    )
                LEFT JOIN SCHOOL_PERSONNEL technician
                    ON technician.Personnel_ID = assignment.Technician_ID
                WHERE wo.Work_Order_ID=?
                """, workOrderId).stream().findFirst().orElseThrow();
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
        Object value = row.get(key);
        return value == null ? 0 : ((Number) value).longValue();
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
