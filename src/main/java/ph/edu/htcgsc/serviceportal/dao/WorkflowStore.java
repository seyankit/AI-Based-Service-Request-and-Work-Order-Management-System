package ph.edu.htcgsc.serviceportal.dao;

import com.google.gson.Gson;
import java.sql.*;
import java.util.*;

/** Small prepared-statement helpers used by the transactional workflow DAOs. */
public final class WorkflowStore {
    private WorkflowStore() { }

    public static List<Map<String, Object>> query(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData metadata = result.getMetaData();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        Object value = result.getObject(i);
                        if (value instanceof Timestamp timestamp) value = timestamp.toInstant().toString();
                        else if (value instanceof java.sql.Date date) value = date.toString();
                        row.put(metadata.getColumnLabel(i), value);
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    public static Map<String, Object> one(Connection connection, String sql, Object... args) throws SQLException {
        List<Map<String, Object>> rows = query(connection, sql, args);
        if (rows.isEmpty()) throw new NoSuchElementException("The requested record was not found.");
        return rows.get(0);
    }

    public static int update(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) { bind(statement, args); return statement.executeUpdate(); }
    }

    public static long insert(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, args);
            if (statement.executeUpdate() != 1) throw new SQLException("The record could not be created.");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("The record identifier was not returned.");
                return keys.getLong(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
    }

    public static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0 : ((Number) value).longValue();
    }

    public static String string(Map<String, Object> row, String key) {
        Object value = row.get(key); return value == null ? null : value.toString();
    }

    public static int activeActor(Connection connection, int actorId, int roleId) throws SQLException {
        List<Map<String, Object>> actors = query(connection, "SELECT sp.Department_ID departmentId FROM SCHOOL_PERSONNEL sp "
                + "JOIN PERSONNEL_ROLE_ASSIGNMENT role ON role.Personnel_ID=sp.Personnel_ID "
                + "WHERE sp.Personnel_ID=? AND role.Role_ID=? AND sp.Account_Status='Active'", actorId, roleId);
        if (actors.isEmpty()) throw new SecurityException("Your account no longer has permission for this action.");
        return (int) number(actors.get(0), "departmentId");
    }

    public static void requestStatus(Connection connection, long requestId, String previous, String next,
                                     int actorId, int roleId, String remarks) throws SQLException {
        if (Objects.equals(previous, next)) return;
        int changed = update(connection, "UPDATE SERVICE_REQUEST SET Current_Status=?, "
                + "Completed_At=CASE WHEN ?='Completed' THEN CURRENT_TIMESTAMP ELSE Completed_At END, "
                + "Closed_At=CASE WHEN ?='Closed' THEN CURRENT_TIMESTAMP ELSE Closed_At END WHERE Request_ID=? AND Current_Status=?",
                next, next, next, requestId, previous);
        if (changed != 1) throw new IllegalStateException("The request has changed. Refresh and try again.");
        update(connection, "INSERT INTO REQUEST_STATUS_HISTORY(Request_ID,Previous_Status,New_Status,Changed_By,Changed_By_Role_ID,Change_Reason) VALUES(?,?,?,?,?,?)",
                requestId, previous, next, actorId, roleId, limit(remarks, 1000));
    }

    public static void workHistory(Connection connection, long id, String previous, String next, String action,
                                   int actorId, String remarks) throws SQLException {
        update(connection, "INSERT INTO WORK_ORDER_HISTORY(Work_Order_ID,Previous_Status,New_Status,Action_Type,Changed_By,Remarks) VALUES(?,?,?,?,?,?)",
                id, previous, next, action, actorId, limit(remarks, 2000));
    }

    public static void audit(Connection connection, int actor, int role, Long requestId, Long workId,
                             String action, String summary, String remarks) throws SQLException {
        update(connection, "INSERT INTO AUDIT_LOG(Actor_Type,Actor_ID,Actor_Role_ID,Request_ID,Work_Order_ID,Action_Type,Entity_Type,Entity_ID,Action_Summary,Details_JSON) "
                + "VALUES('PERSONNEL',?,?,?,?,?,?,?,?,?)", actor, role, requestId, workId, action,
                workId == null ? "SERVICE_REQUEST" : "WORK_ORDER", workId == null ? requestId : workId,
                limit(summary, 500), new Gson().toJson(Map.of("remarks", remarks == null ? "" : remarks)));
    }

    public static void notify(Connection connection, int recipient, int actor, Long requestId, Long workId,
                              String type, String title, String message, String event) throws SQLException {
        update(connection, "INSERT INTO NOTIFICATION(Recipient_ID,Triggered_By_ID,Request_ID,Work_Order_ID,Notification_Type,Notification_Title,Notification_Message,Action_URL,Notification_Event_Key) "
                + "VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE Notification_ID=Notification_ID",
                recipient, actor, requestId, workId, type, title, limit(message, 1000),
                workId == null ? "#requests" : "#work-orders", event);
    }

    public static void notifyRole(Connection connection, int roleId, Integer departmentId, int actor, Long requestId, Long workId,
                                  String type, String title, String message, String event) throws SQLException {
        for (Map<String, Object> recipient : query(connection,
                "SELECT sp.Personnel_ID id FROM SCHOOL_PERSONNEL sp JOIN PERSONNEL_ROLE_ASSIGNMENT r ON r.Personnel_ID=sp.Personnel_ID "
                        + "WHERE sp.Account_Status='Active' AND r.Role_ID=? AND (? IS NULL OR sp.Department_ID=?)",
                roleId, departmentId, departmentId)) {
            notify(connection, (int) number(recipient, "id"), actor, requestId, workId, type, title, message, event);
        }
    }

    private static String limit(String value, int maximum) { return value == null ? null : value.substring(0, Math.min(value.length(), maximum)); }
}
