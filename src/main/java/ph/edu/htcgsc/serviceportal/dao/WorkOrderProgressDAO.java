package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.util.WorkflowPolicy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.activeActor;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.audit;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.insert;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.number;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.query;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.string;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.update;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.workHistory;

/** Technician-only work-order reads and lifecycle progress mutations. */
public final class WorkOrderProgressDAO {
    public static final int TECHNICIAN_ROLE_ID = 4;

    public record MutationInput(
            String action,
            long workOrderId,
            Integer progressPercentage,
            String progressNotes,
            String completionSummary
    ) { }

    public enum Failure {
        WORK_ORDER_NOT_FOUND,
        ASSIGNMENT_NOT_FOUND,
        NOT_CURRENT_ASSIGNEE,
        STALE_STATE
    }

    public static final class ProgressException extends Exception {
        private final Failure failure;

        public ProgressException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }

    public List<Map<String, Object>> list(int actor, int role, Long workOrderId)
            throws SQLException {
        requireTechnician(role);
        try (Connection connection = DatabaseConnection.getConnection()) {
            activeActor(connection, actor, role);
            return visibleWorkOrders(connection, actor, workOrderId);
        }
    }

    public Map<String, Object> mutate(int actor, int role, MutationInput input)
            throws SQLException, ProgressException {
        requireTechnician(role);
        if (input == null) throw new IllegalArgumentException("A progress request is required.");
        if (input.workOrderId() <= 0) {
            throw new IllegalArgumentException("The work-order ID must be positive.");
        }

        String action = input.action() == null ? "" : input.action().trim();
        if (!List.of("acknowledge", "start", "progress", "hold", "resume", "complete")
                .contains(action)) {
            throw new IllegalArgumentException("Unknown work-order action.");
        }
        String notes = WorkflowPolicy.text(input.progressNotes(), "Progress notes", 5, 2000);
        String completionSummary = null;
        if ("complete".equals(action)) {
            // WORK_ORDER.CK_WORK_ORDER_COMPLETION_DATA requires ten characters.
            completionSummary = WorkflowPolicy.text(input.completionSummary(),
                    "Completion summary", 10, 2000);
        }
        if ("progress".equals(action)) {
            if (input.progressPercentage() == null
                    || input.progressPercentage() < 1
                    || input.progressPercentage() > 99) {
                throw new IllegalArgumentException("Progress updates must be from 1 to 99 percent.");
            }
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                activeActor(connection, actor, role);
                Map<String, Object> workOrder = lockWorkOrder(connection, input.workOrderId());
                if (workOrder == null) {
                    throw new ProgressException(Failure.WORK_ORDER_NOT_FOUND,
                            "The requested work order was not found.");
                }
                Map<String, Object> assignment = lockCurrentAssignment(connection, input.workOrderId());
                if (assignment == null) {
                    throw new ProgressException(Failure.ASSIGNMENT_NOT_FOUND,
                            "The work order has no current technician assignment.");
                }
                if (number(assignment, "technicianId") != actor) {
                    throw new ProgressException(Failure.NOT_CURRENT_ASSIGNEE,
                            "This work order is not assigned to you.");
                }

                String previousStatus = string(workOrder, "status");
                int percentage = switch (action) {
                    case "progress" -> input.progressPercentage();
                    case "hold", "resume" -> latestPercentage(connection,
                            number(assignment, "assignmentId"));
                    default -> 0;
                };
                WorkflowPolicy.Transition transition =
                        WorkflowPolicy.transition(previousStatus, action, percentage);
                Timestamp timestamp = Timestamp.from(Instant.now());
                long workOrderId = input.workOrderId();
                long assignmentId = number(assignment, "assignmentId");

                int changed;
                if ("acknowledge".equals(action)) {
                    changed = update(connection,
                            "UPDATE WORK_ORDER SET Work_Status=?,Acknowledged_At=? "
                                    + "WHERE Work_Order_ID=? AND Work_Status=? AND Acknowledged_At IS NULL",
                            transition.status(), timestamp, workOrderId, previousStatus);
                    if (changed == 1) {
                        changed = update(connection,
                                "UPDATE WORK_ORDER_ASSIGNMENT SET Acknowledged_At=? "
                                        + "WHERE Assignment_ID=? AND Is_Current=TRUE AND Acknowledged_At IS NULL",
                                timestamp, assignmentId);
                    }
                } else if ("start".equals(action)) {
                    changed = update(connection,
                            "UPDATE WORK_ORDER SET Work_Status=?,Actual_Start_At=? "
                                    + "WHERE Work_Order_ID=? AND Work_Status=? AND Actual_Start_At IS NULL",
                            transition.status(), timestamp, workOrderId, previousStatus);
                } else if ("complete".equals(action)) {
                    changed = update(connection,
                            "UPDATE WORK_ORDER SET Work_Status=?,Completion_Summary=?,Completed_At=? "
                                    + "WHERE Work_Order_ID=? AND Work_Status=? AND Completed_At IS NULL",
                            transition.status(), completionSummary, timestamp, workOrderId, previousStatus);
                } else if ("progress".equals(action)) {
                    changed = update(connection,
                            "UPDATE WORK_ORDER SET Updated_At=? "
                                    + "WHERE Work_Order_ID=? AND Work_Status=?",
                            timestamp, workOrderId, previousStatus);
                } else {
                    changed = update(connection,
                            "UPDATE WORK_ORDER SET Work_Status=? "
                                    + "WHERE Work_Order_ID=? AND Work_Status=?",
                            transition.status(), workOrderId, previousStatus);
                }
                if (changed != 1) {
                    throw new ProgressException(Failure.STALE_STATE,
                            "The work order changed. Refresh and try again.");
                }

                long progressId = insert(connection,
                        "INSERT INTO WORK_ORDER_PROGRESS "
                                + "(Work_Order_ID,Assignment_ID,Updated_By,Update_Type,Previous_Work_Status,"
                                + "New_Work_Status,Progress_Percentage,Progress_Notes,Is_Requester_Visible,Recorded_At) "
                                + "VALUES(?,?,?,?,?,?,?,?,FALSE,?)",
                        workOrderId, assignmentId, actor, transition.updateType(), previousStatus,
                        transition.status(), transition.percentage(), notes, timestamp);

                if (!previousStatus.equals(transition.status())) {
                    workHistory(connection, workOrderId, previousStatus, transition.status(),
                            historyAction(action), actor, notes);
                }

                long requestId = number(workOrder, "requestId");
                String workOrderNumber = string(workOrder, "workOrderNumber");
                String auditAction = auditAction(action);
                audit(connection, actor, role, requestId, workOrderId, auditAction,
                        "Technician " + action + " recorded for work order " + workOrderNumber + ".", notes);

                int recipient = (int) number(workOrder, "createdBy");
                if (recipient <= 0) {
                    throw new SQLException("The work order creator is unavailable for notification.");
                }
                if (query(connection,
                        "SELECT sp.Personnel_ID FROM SCHOOL_PERSONNEL sp "
                                + "JOIN PERSONNEL_ROLE_ASSIGNMENT role ON role.Personnel_ID=sp.Personnel_ID "
                                + "WHERE sp.Personnel_ID=? AND sp.Account_Status='Active' AND role.Role_ID=2",
                        recipient).isEmpty()) {
                    throw new SQLException("The work order creator is not an eligible notification recipient.");
                }
                String notificationType = notificationType(action);
                WorkflowStore.notify(connection, recipient, actor, requestId, workOrderId,
                        notificationType, "Work order update",
                        "Work order " + workOrderNumber + " was updated by its technician.",
                        "WORK_ORDER_PROGRESS:" + workOrderId + ":" + progressId + ":" + action);

                Map<String, Object> result = visibleWorkOrders(connection, actor, workOrderId)
                        .stream().findFirst().orElseThrow(() -> new ProgressException(
                                Failure.STALE_STATE, "The work order could not be reloaded."));
                result.put("progressId", progressId);
                connection.commit();
                return result;
            } catch (ProgressException | RuntimeException exception) {
                rollback(connection);
                throw exception;
            } catch (SQLException exception) {
                rollback(connection);
                throw exception;
            }
        }
    }

    private List<Map<String, Object>> visibleWorkOrders(Connection connection, int actor, Long workOrderId)
            throws SQLException {
        String filter = workOrderId == null ? "" : " AND wo.Work_Order_ID=?";
        String sql = """
                SELECT wo.Work_Order_ID AS workOrderId,
                       wo.Work_Order_Number AS workOrderNumber,
                       wo.Request_ID AS requestId,
                       sr.Request_Number AS requestNumber,
                       sr.Request_Title AS requestTitle,
                       sr.Request_Description AS requestDescription,
                       sr.Request_Location AS requestLocation,
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
                       assignment.Assignment_ID AS assignmentId,
                       assignment.Assignment_Sequence AS assignmentSequence,
                       assignment.Technician_ID AS technicianId,
                       CONCAT(technician.First_Name, ' ', technician.Last_Name) AS technicianName,
                       assignment.Assigned_At AS assignedAt,
                       assignment.Acknowledged_At AS assignmentAcknowledgedAt,
                       assignment.Assignment_Notes AS assignmentNotes
                FROM WORK_ORDER wo
                JOIN SERVICE_REQUEST sr ON sr.Request_ID=wo.Request_ID
                JOIN DEPARTMENT department ON department.Department_ID=wo.Department_ID
                JOIN WORK_ORDER_ASSIGNMENT assignment
                  ON assignment.Work_Order_ID=wo.Work_Order_ID
                 AND assignment.Is_Current=TRUE
                 AND assignment.Assignment_ID=(
                     SELECT MAX(current_assignment.Assignment_ID)
                     FROM WORK_ORDER_ASSIGNMENT current_assignment
                     WHERE current_assignment.Work_Order_ID=wo.Work_Order_ID
                       AND current_assignment.Is_Current=TRUE)
                JOIN SCHOOL_PERSONNEL technician ON technician.Personnel_ID=assignment.Technician_ID
                WHERE assignment.Technician_ID=?%s
                ORDER BY wo.Created_At DESC,wo.Work_Order_ID DESC
                """.formatted(filter);
        List<Map<String, Object>> rows = workOrderId == null
                ? query(connection, sql, actor)
                : query(connection, sql, actor, workOrderId);
        for (Map<String, Object> row : rows) {
            long id = number(row, "workOrderId");
            row.put("progressHistory", query(connection,
                    "SELECT Progress_ID AS progressId,Assignment_ID AS assignmentId,Updated_By AS updatedBy,"
                            + "Update_Type AS updateType,Previous_Work_Status AS previousStatus,"
                            + "New_Work_Status AS newStatus,Progress_Percentage AS progressPercentage,"
                            + "Progress_Notes AS progressNotes,Is_Requester_Visible AS requesterVisible,"
                            + "Recorded_At AS recordedAt FROM WORK_ORDER_PROGRESS "
                            + "WHERE Work_Order_ID=? ORDER BY Recorded_At ASC,Progress_ID ASC", id));
        }
        return rows;
    }

    private Map<String, Object> lockWorkOrder(Connection connection, long id) throws SQLException {
        List<Map<String, Object>> rows = query(connection,
                "SELECT Work_Order_ID AS workOrderId,Work_Order_Number AS workOrderNumber,"
                        + "Request_ID AS requestId,Created_By AS createdBy,Work_Status AS status "
                        + "FROM WORK_ORDER WHERE Work_Order_ID=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> lockCurrentAssignment(Connection connection, long workOrderId)
            throws SQLException {
        List<Map<String, Object>> rows = query(connection,
                "SELECT Assignment_ID AS assignmentId,Technician_ID AS technicianId "
                        + "FROM WORK_ORDER_ASSIGNMENT WHERE Work_Order_ID=? AND Is_Current=TRUE "
                        + "ORDER BY Assignment_ID DESC LIMIT 1 FOR UPDATE", workOrderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int latestPercentage(Connection connection, long assignmentId) throws SQLException {
        List<Map<String, Object>> rows = query(connection,
                "SELECT Progress_Percentage AS percentage FROM WORK_ORDER_PROGRESS "
                        + "WHERE Assignment_ID=? ORDER BY Recorded_At DESC,Progress_ID DESC LIMIT 1",
                assignmentId);
        return rows.isEmpty() ? 0 : (int) number(rows.get(0), "percentage");
    }

    private void requireTechnician(int role) {
        if (role != TECHNICIAN_ROLE_ID) {
            throw new SecurityException("Service Technician access is required.");
        }
    }

    private String historyAction(String action) {
        return switch (action) {
            case "acknowledge" -> "Acknowledged";
            case "start" -> "Started";
            case "hold" -> "On Hold";
            case "resume" -> "Resumed";
            case "complete" -> "Completed";
            default -> action;
        };
    }

    private String auditAction(String action) {
        return switch (action) {
            case "acknowledge" -> "WORK_ORDER_ACKNOWLEDGED";
            case "start" -> "WORK_ORDER_STARTED";
            case "progress" -> "WORK_ORDER_PROGRESS_UPDATED";
            case "hold" -> "WORK_ORDER_ON_HOLD";
            case "resume" -> "WORK_ORDER_RESUMED";
            case "complete" -> "WORK_ORDER_COMPLETED";
            default -> throw new IllegalArgumentException("Unknown work-order action.");
        };
    }

    private String notificationType(String action) {
        return switch (action) {
            case "acknowledge" -> "WORK_ACKNOWLEDGED";
            case "start" -> "WORK_STARTED";
            case "progress" -> "WORK_PROGRESS_UPDATED";
            case "hold" -> "WORK_ON_HOLD";
            case "resume" -> "WORK_RESUMED";
            case "complete" -> "WORK_COMPLETED";
            default -> throw new IllegalArgumentException("Unknown work-order action.");
        };
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original failure.
        }
    }
}
