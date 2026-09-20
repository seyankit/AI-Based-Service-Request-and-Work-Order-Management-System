package ph.edu.htcgsc.serviceportal.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NotificationDAO {

    private static final Logger LOGGER =
            Logger.getLogger(NotificationDAO.class.getName());

    private static final int MAX_LIST_ROWS = 50;

    private NotificationDAO() {
        // utility DAO-style class
    }

    public static List<Map<String, Object>> findRecentForRecipient(
            Connection connection, int recipientId) throws SQLException {

        String sql =
                "SELECT "
                        + "Notification_ID, "
                        + "Triggered_By_ID, "
                        + "Request_ID, "
                        + "Work_Order_ID, "
                        + "Notification_Type, "
                        + "Notification_Title, "
                        + "Notification_Message, "
                        + "Action_URL, "
                        + "Is_Read, "
                        + "Read_At, "
                        + "Created_At "
                        + "FROM NOTIFICATION "
                        + "WHERE Recipient_ID = ? "
                        + "ORDER BY Created_At DESC, Notification_ID DESC "
                        + "LIMIT ?";

        try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

            statement.setInt(1, recipientId);
            statement.setInt(2, MAX_LIST_ROWS);

            List<Map<String, Object>> rows = new ArrayList<>();

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapNotificationRow(resultSet));
                }
            }

            return rows;
        }
    }

    public static int unreadCountForRecipient(
            Connection connection, int recipientId) throws SQLException {

        String sql =
                "SELECT COUNT(*) "
                        + "FROM NOTIFICATION "
                        + "WHERE Recipient_ID = ? "
                        + "AND Is_Read = 0";

        try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

            statement.setInt(1, recipientId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }

            return 0;
        }
    }

    public static int markReadForRecipient(
            Connection connection, long notificationId, int recipientId)
            throws SQLException {

        String sql =
                "UPDATE NOTIFICATION "
                        + "SET "
                        + "Is_Read = TRUE, "
                        + "Read_At = CURRENT_TIMESTAMP, "
                        + "Updated_At = CURRENT_TIMESTAMP "
                        + "WHERE Notification_ID = ? "
                        + "AND Recipient_ID = ? "
                        + "AND Is_Read = 0";

        try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

            statement.setLong(1, notificationId);
            statement.setInt(2, recipientId);

            return statement.executeUpdate();
        }
    }

    public static int markAllReadForRecipient(
            Connection connection, int recipientId) throws SQLException {

        String sql =
                "UPDATE NOTIFICATION "
                        + "SET "
                        + "Is_Read = TRUE, "
                        + "Read_At = CURRENT_TIMESTAMP, "
                        + "Updated_At = CURRENT_TIMESTAMP "
                        + "WHERE Recipient_ID = ? "
                        + "AND Is_Read = 0";

        try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

            statement.setInt(1, recipientId);

            return statement.executeUpdate();
        }
    }

    public static Map<String, Object> findForRecipient(
            Connection connection, long notificationId, int recipientId) throws SQLException {

        String sql =
                "SELECT "
                        + "Notification_ID, "
                        + "Triggered_By_ID, "
                        + "Request_ID, "
                        + "Work_Order_ID, "
                        + "Notification_Type, "
                        + "Notification_Title, "
                        + "Notification_Message, "
                        + "Action_URL, "
                        + "Is_Read, "
                        + "Read_At, "
                        + "Created_At "
                        + "FROM NOTIFICATION "
                        + "WHERE Notification_ID = ? "
                        + "AND Recipient_ID = ?";

        try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

            statement.setLong(1, notificationId);
            statement.setInt(2, recipientId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapNotificationRow(resultSet);
                }
            }

            return null;
        }
    }

    private static Map<String, Object> mapNotificationRow(
            ResultSet resultSet) throws SQLException {

        Timestamp createdAt = resultSet.getTimestamp("Created_At");
        Timestamp readAt = resultSet.getTimestamp("Read_At");

        Map<String, Object> row = new java.util.LinkedHashMap<>();

        row.put("notificationId", resultSet.getLong("Notification_ID"));
        row.put("triggeredById",
                resultSet.getObject("Triggered_By_ID") == null
                        ? null
                        : resultSet.getLong("Triggered_By_ID"));
        row.put("requestId",
                resultSet.getObject("Request_ID") == null
                        ? null
                        : resultSet.getLong("Request_ID"));
        row.put("workOrderId",
                resultSet.getObject("Work_Order_ID") == null
                        ? null
                        : resultSet.getLong("Work_Order_ID"));
        row.put("notificationType",
                resultSet.getString("Notification_Type"));
        row.put("title", resultSet.getString("Notification_Title"));
        row.put("message", resultSet.getString("Notification_Message"));
        row.put("actionUrl", resultSet.getString("Action_URL"));
        row.put("isRead", Boolean.TRUE.equals(resultSet.getBoolean("Is_Read")));
        row.put("createdAt",
                createdAt == null ? null : createdAt.toInstant().toString());
        row.put("readAt",
                readAt == null ? null : readAt.toInstant().toString());

        return Collections.unmodifiableMap(row);
    }
}
