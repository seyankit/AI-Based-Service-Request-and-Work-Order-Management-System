package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.activeActor;

/** Read-only, administrator-scoped presentation of the permanent audit trail. */
public final class AuditLogDAO {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAXIMUM_LIMIT = 100;
    private static final int ADMINISTRATOR_ROLE_ID = SessionUtil.SERVICE_ADMINISTRATOR_ROLE_ID;
    private static final Pattern ACTION_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{2,59}");

    public record Filters(String actionType, String outcome, Long actorId, Long requestId,
                          Long workOrderId, Instant from, Instant to) {
        public Filters {
            actionType = normalizeActionType(actionType);
            outcome = normalizeOutcome(outcome);
            actorId = positiveOrNull(actorId, "actorId");
            requestId = positiveOrNull(requestId, "requestId");
            workOrderId = positiveOrNull(workOrderId, "workOrderId");
            if (from != null && to != null && from.isAfter(to)) {
                throw new IllegalArgumentException("The from parameter must not be after the to parameter.");
            }
        }
    }

    public record Cursor(Instant createdAt, long auditId) {
        public Cursor {
            if (createdAt == null || auditId <= 0) {
                throw new IllegalArgumentException("The cursor is invalid.");
            }
        }
    }

    public record AuditEntry(long auditId, String createdAt, String actionType, String outcome,
                             String entityType, Long entityId, Long requestId, String requestNumber,
                             Long workOrderId, String workOrderNumber, Long actorId, String actorName,
                             String actorRole, String summary) { }

    public record Page(List<AuditEntry> entries, String nextCursor) {
        public Page {
            entries = List.copyOf(entries);
        }
    }

    public Page findForAdministrator(int actorId, Filters filters, Cursor cursor, int limit)
            throws SQLException {
        if (actorId <= 0) throw new IllegalArgumentException("The actor ID must be positive.");
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("The limit must be from 1 to " + MAXIMUM_LIMIT + ".");
        }
        Objects.requireNonNull(filters, "filters");

        try (Connection connection = DatabaseConnection.getConnection()) {
            activeActor(connection, actorId, ADMINISTRATOR_ROLE_ID);
            return find(connection, filters, cursor, limit);
        }
    }

    static Page find(Connection connection, Filters filters, Cursor cursor, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT al.Audit_ID, al.Created_At, al.Action_Type, al.Action_Outcome,
                       al.Entity_Type, al.Entity_ID, al.Request_ID, sr.Request_Number,
                       al.Work_Order_ID, wo.Work_Order_Number, al.Actor_ID,
                       CONCAT_WS(' ', actor.First_Name, actor.Last_Name) AS Actor_Name,
                       role.Role_Name AS Actor_Role, al.Action_Summary
                FROM AUDIT_LOG al
                LEFT JOIN SCHOOL_PERSONNEL actor ON actor.Personnel_ID = al.Actor_ID
                LEFT JOIN PERSONNEL_ROLE role ON role.Role_ID = al.Actor_Role_ID
                LEFT JOIN SERVICE_REQUEST sr ON sr.Request_ID = al.Request_ID
                LEFT JOIN WORK_ORDER wo ON wo.Work_Order_ID = al.Work_Order_ID
                WHERE 1=1
                """);
        List<Object> values = new ArrayList<>();
        appendFilters(sql, values, filters);
        if (cursor != null) {
            sql.append(" AND (al.Created_At < ? OR (al.Created_At = ? AND al.Audit_ID < ?))");
            Timestamp timestamp = Timestamp.from(cursor.createdAt());
            values.add(timestamp);
            values.add(timestamp);
            values.add(cursor.auditId());
        }
        sql.append(" ORDER BY al.Created_At DESC, al.Audit_ID DESC LIMIT ?");
        values.add(limit + 1);

        List<AuditEntry> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, values);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) rows.add(mapEntry(resultSet));
            }
        }

        String nextCursor = null;
        if (rows.size() > limit) {
            AuditEntry extra = rows.remove(rows.size() - 1);
            nextCursor = encodeCursor(new Cursor(Instant.parse(extra.createdAt()), extra.auditId()));
        }
        return new Page(rows, nextCursor);
    }

    public static Filters parseFilters(String actionType, String outcome, String actorId,
                                       String requestId, String workOrderId, String from, String to) {
        return new Filters(actionType, outcome, parsePositive(actorId, "actorId"),
                parsePositive(requestId, "requestId"), parsePositive(workOrderId, "workOrderId"),
                parseInstant(from, "from"), parseInstant(to, "to"));
    }

    public static Cursor parseCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new IllegalArgumentException("The cursor parameter is invalid.");
        }
    }

    private static String encodeCursor(Cursor cursor) {
        String value = cursor.createdAt() + "|" + cursor.auditId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void appendFilters(StringBuilder sql, List<Object> values, Filters filters) {
        if (filters.actionType() != null) { sql.append(" AND al.Action_Type = ?"); values.add(filters.actionType()); }
        if (filters.outcome() != null) { sql.append(" AND al.Action_Outcome = ?"); values.add(filters.outcome()); }
        if (filters.actorId() != null) { sql.append(" AND al.Actor_ID = ?"); values.add(filters.actorId()); }
        if (filters.requestId() != null) { sql.append(" AND al.Request_ID = ?"); values.add(filters.requestId()); }
        if (filters.workOrderId() != null) { sql.append(" AND al.Work_Order_ID = ?"); values.add(filters.workOrderId()); }
        if (filters.from() != null) { sql.append(" AND al.Created_At >= ?"); values.add(Timestamp.from(filters.from())); }
        if (filters.to() != null) { sql.append(" AND al.Created_At <= ?"); values.add(Timestamp.from(filters.to())); }
    }

    private static AuditEntry mapEntry(ResultSet resultSet) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp("Created_At");
        return new AuditEntry(resultSet.getLong("Audit_ID"), timestamp == null ? null : timestamp.toInstant().toString(),
                resultSet.getString("Action_Type"), resultSet.getString("Action_Outcome"),
                resultSet.getString("Entity_Type"), nullableLong(resultSet, "Entity_ID"),
                nullableLong(resultSet, "Request_ID"), resultSet.getString("Request_Number"),
                nullableLong(resultSet, "Work_Order_ID"), resultSet.getString("Work_Order_Number"),
                nullableLong(resultSet, "Actor_ID"), blankToNull(resultSet.getString("Actor_Name")),
                resultSet.getString("Actor_Role"), resultSet.getString("Action_Summary"));
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) statement.setObject(index + 1, values.get(index));
    }

    private static String normalizeActionType(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!ACTION_TYPE.matcher(normalized).matches()) throw new IllegalArgumentException("The actionType parameter is invalid.");
        return normalized;
    }

    private static String normalizeOutcome(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SUCCESS", "FAILURE", "DENIED").contains(normalized)) {
            throw new IllegalArgumentException("The outcome parameter is invalid.");
        }
        return normalized;
    }

    private static Long parsePositive(String value, String name) {
        if (value == null || value.isBlank()) return null;
        try { return positiveOrNull(Long.parseLong(value.trim()), name); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("The " + name + " parameter must be a positive whole number."); }
    }

    private static Long positiveOrNull(Long value, String name) {
        if (value == null) return null;
        if (value <= 0) throw new IllegalArgumentException("The " + name + " parameter must be a positive whole number.");
        return value;
    }

    private static Instant parseInstant(String value, String name) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value.trim()); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException("The " + name + " parameter must be an ISO-8601 instant."); }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
