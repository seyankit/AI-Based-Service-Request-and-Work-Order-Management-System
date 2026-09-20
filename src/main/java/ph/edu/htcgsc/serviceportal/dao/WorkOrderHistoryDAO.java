package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.activeActor;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.number;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.query;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.string;

/** Read-only, role-scoped presentation timeline for one work order. */
public final class WorkOrderHistoryDAO {
    private static final int ADMINISTRATOR_ROLE_ID = SessionUtil.SERVICE_ADMINISTRATOR_ROLE_ID;
    private static final int TECHNICIAN_ROLE_ID = SessionUtil.SERVICE_PERSONNEL_ROLE_ID;

    /*
     * Phase 5A writes both a progress row and a work-order history row for
     * these technician lifecycle actions. WORK_ORDER_PROGRESS is canonical
     * for them because it contains the percentage and progress notes.
     */
    private static final Map<String, String> DUPLICATE_TECHNICIAN_HISTORY_ACTIONS = Map.of(
            "Acknowledged", "Acknowledged",
            "Started", "Work Started",
            "On Hold", "On Hold",
            "Resumed", "Resumed",
            "Completed", "Completed");

    public record LookupResult(
            boolean found,
            long workOrderId,
            String workOrderNumber,
            List<Map<String, Object>> workOrderEvents,
            List<Map<String, Object>> requestEvents
    ) {
        public LookupResult {
            workOrderEvents = List.copyOf(workOrderEvents);
            requestEvents = List.copyOf(requestEvents);
        }
    }

    public LookupResult findForActor(int actor, int role, long workOrderId) throws SQLException {
        requireSupportedRole(role);
        if (actor <= 0) throw new IllegalArgumentException("The actor ID must be positive.");
        if (workOrderId <= 0) throw new IllegalArgumentException("The work-order ID must be positive.");

        try (Connection connection = DatabaseConnection.getConnection()) {
            activeActor(connection, actor, role);
            Map<String, Object> workOrder = authorizedWorkOrder(connection, actor, role, workOrderId);
            if (workOrder == null) {
                return new LookupResult(false, workOrderId, null, List.of(), List.of());
            }

            List<Map<String, Object>> workOrderEvents = normalizeWorkOrderEvents(
                    query(connection, "SELECT History_ID AS historyId,Action_Type AS actionType,"
                                    + "Previous_Status AS previousStatus,New_Status AS newStatus,"
                                    + "Remarks AS remarks,Changed_By AS actorId,Changed_At AS occurredAt "
                                    + "FROM WORK_ORDER_HISTORY WHERE Work_Order_ID=? "
                                    + "ORDER BY Changed_At ASC,History_ID ASC",
                            workOrderId),
                    query(connection, "SELECT Progress_ID AS progressId,Update_Type AS updateType,"
                                    + "Previous_Work_Status AS previousStatus,New_Work_Status AS newStatus,"
                                    + "Progress_Percentage AS percentage,Progress_Notes AS remarks,"
                                    + "Updated_By AS actorId,Recorded_At AS occurredAt "
                                    + "FROM WORK_ORDER_PROGRESS WHERE Work_Order_ID=? "
                                    + "ORDER BY Recorded_At ASC,Progress_ID ASC",
                            workOrderId));

            List<Map<String, Object>> requestEvents = role == ADMINISTRATOR_ROLE_ID
                    ? requestEvents(connection, number(workOrder, "requestId"))
                    : List.of();
            return new LookupResult(true, number(workOrder, "workOrderId"),
                    string(workOrder, "workOrderNumber"), workOrderEvents, requestEvents);
        }
    }

    static List<Map<String, Object>> normalizeWorkOrderEvents(
            List<Map<String, Object>> historyRows,
            List<Map<String, Object>> progressRows
    ) {
        List<SortableEvent> events = new ArrayList<>();
        Set<String> progressActionTypes = new HashSet<>();
        for (Map<String, Object> row : progressRows) {
            progressActionTypes.add(string(row, "updateType"));
        }
        for (Map<String, Object> row : historyRows) {
            String actionType = string(row, "actionType");
            String correspondingProgressAction = DUPLICATE_TECHNICIAN_HISTORY_ACTIONS.get(actionType);
            if (correspondingProgressAction != null
                    && progressActionTypes.contains(correspondingProgressAction)) continue;
            events.add(new SortableEvent(historyEvent(row), occurredAt(row), 0, number(row, "historyId")));
        }
        for (Map<String, Object> row : progressRows) {
            events.add(new SortableEvent(progressEvent(row), occurredAt(row), 1, number(row, "progressId")));
        }
        events.sort(Comparator.comparing(SortableEvent::occurredAt)
                .thenComparingInt(SortableEvent::sourceOrder)
                .thenComparingLong(SortableEvent::sourceId));
        return events.stream().map(SortableEvent::event).toList();
    }

    private Map<String, Object> authorizedWorkOrder(
            Connection connection,
            int actor,
            int role,
            long workOrderId
    ) throws SQLException {
        String sql = role == ADMINISTRATOR_ROLE_ID
                ? "SELECT Work_Order_ID AS workOrderId,Work_Order_Number AS workOrderNumber,"
                        + "Request_ID AS requestId FROM WORK_ORDER WHERE Work_Order_ID=?"
                : "SELECT wo.Work_Order_ID AS workOrderId,wo.Work_Order_Number AS workOrderNumber,"
                        + "wo.Request_ID AS requestId FROM WORK_ORDER wo "
                        + "JOIN WORK_ORDER_ASSIGNMENT assignment "
                        + "ON assignment.Work_Order_ID=wo.Work_Order_ID "
                        + "AND assignment.Is_Current=TRUE "
                        + "WHERE wo.Work_Order_ID=? AND assignment.Technician_ID=?";
        List<Map<String, Object>> rows = role == ADMINISTRATOR_ROLE_ID
                ? query(connection, sql, workOrderId)
                : query(connection, sql, workOrderId, actor);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> requestEvents(Connection connection, long requestId)
            throws SQLException {
        List<Map<String, Object>> rows = query(connection,
                "SELECT History_ID AS historyId,Previous_Status AS previousStatus,"
                        + "New_Status AS newStatus,Change_Reason AS remarks,Changed_By AS actorId,"
                        + "Changed_At AS occurredAt FROM REQUEST_STATUS_HISTORY WHERE Request_ID=? "
                        + "ORDER BY Changed_At ASC,History_ID ASC", requestId);
        List<Map<String, Object>> events = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("source", "REQUEST_STATUS_HISTORY");
            event.put("eventType", "Request Status Changed");
            event.put("title", "Request status changed");
            event.put("previousStatus", string(row, "previousStatus"));
            event.put("newStatus", string(row, "newStatus"));
            event.put("percentage", null);
            event.put("remarks", string(row, "remarks"));
            event.put("actorId", number(row, "actorId"));
            event.put("occurredAt", string(row, "occurredAt"));
            events.add(event);
        }
        return events;
    }

    private static Map<String, Object> historyEvent(Map<String, Object> row) {
        String actionType = string(row, "actionType");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("source", "WORK_ORDER_HISTORY");
        event.put("eventType", actionType);
        event.put("title", actionType);
        event.put("previousStatus", string(row, "previousStatus"));
        event.put("newStatus", string(row, "newStatus"));
        event.put("percentage", null);
        event.put("remarks", string(row, "remarks"));
        event.put("actorId", number(row, "actorId"));
        event.put("occurredAt", string(row, "occurredAt"));
        return event;
    }

    private static Map<String, Object> progressEvent(Map<String, Object> row) {
        String updateType = string(row, "updateType");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("source", "WORK_ORDER_PROGRESS");
        event.put("eventType", updateType);
        event.put("title", updateType);
        event.put("previousStatus", string(row, "previousStatus"));
        event.put("newStatus", string(row, "newStatus"));
        event.put("percentage", row.get("percentage"));
        event.put("remarks", string(row, "remarks"));
        event.put("actorId", number(row, "actorId"));
        event.put("occurredAt", string(row, "occurredAt"));
        return event;
    }

    private static Instant occurredAt(Map<String, Object> row) {
        String value = string(row, "occurredAt");
        try {
            return value == null ? Instant.EPOCH : Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return Instant.EPOCH;
        }
    }

    private void requireSupportedRole(int role) {
        if (role != ADMINISTRATOR_ROLE_ID && role != TECHNICIAN_ROLE_ID) {
            throw new SecurityException("Work-order history access is not permitted.");
        }
    }

    private record SortableEvent(
            Map<String, Object> event,
            Instant occurredAt,
            int sourceOrder,
            long sourceId
    ) { }
}
