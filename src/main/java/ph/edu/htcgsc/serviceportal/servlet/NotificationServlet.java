package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.dao.NotificationDAO;
import ph.edu.htcgsc.serviceportal.util.ApiJson;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NotificationServlet extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(NotificationServlet.class.getName());

    @FunctionalInterface
    interface UnreadCountSupplier {
        int count(int personnelId) throws SQLException;
    }

    @FunctionalInterface
    interface NotificationLister {
        List<Map<String, Object>> list(int personnelId) throws SQLException;
    }

    @FunctionalInterface
    interface NotificationMutator {
        int markRead(long notificationId, int personnelId) throws SQLException;
    }

    @FunctionalInterface
    interface NotificationAllMutator {
        int markAllRead(int personnelId) throws SQLException;
    }

    @FunctionalInterface
    interface NotificationReader {
        Map<String, Object> read(long notificationId, int personnelId) throws SQLException;
    }

    private final UnreadCountSupplier unreadCountSupplier;
    private final NotificationLister notificationLister;
    private final NotificationMutator notificationMutator;
    private final NotificationAllMutator notificationAllMutator;
    private final NotificationReader notificationReader;

    public NotificationServlet() {
        this.unreadCountSupplier = personnelId -> {
            try (Connection connection = DatabaseConnection.getConnection()) {
                return NotificationDAO.unreadCountForRecipient(connection, personnelId);
            }
        };
        this.notificationLister = personnelId -> {
            try (Connection connection = DatabaseConnection.getConnection()) {
                return NotificationDAO.findRecentForRecipient(connection, personnelId);
            }
        };
        this.notificationMutator = (notificationId, personnelId) -> {
            try (Connection connection = DatabaseConnection.getConnection()) {
                return NotificationDAO.markReadForRecipient(connection, notificationId, personnelId);
            }
        };
        this.notificationAllMutator = personnelId -> {
            try (Connection connection = DatabaseConnection.getConnection()) {
                return NotificationDAO.markAllReadForRecipient(connection, personnelId);
            }
        };
        this.notificationReader = (notificationId, personnelId) -> {
            try (Connection connection = DatabaseConnection.getConnection()) {
                return NotificationDAO.findForRecipient(connection, notificationId, personnelId);
            }
        };
    }

    NotificationServlet(
            UnreadCountSupplier unreadCountSupplier,
            NotificationLister notificationLister,
            NotificationMutator notificationMutator,
            NotificationAllMutator notificationAllMutator,
            NotificationReader notificationReader) {
        this.unreadCountSupplier = unreadCountSupplier;
        this.notificationLister = notificationLister;
        this.notificationMutator = notificationMutator;
        this.notificationAllMutator = notificationAllMutator;
        this.notificationReader = notificationReader;
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws IOException {

        if (!SessionUtil.isAuthenticated(request)) {
            ApiJson.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required.");
            return;
        }

        Integer personnelId =
                SessionUtil.getAuthenticatedPersonnelId(request);

        if (personnelId == null) {
            ApiJson.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid.");
            return;
        }

        try {
            int unreadCount = unreadCountSupplier.count(personnelId);

            List<Map<String, Object>> notifications =
                    notificationLister.list(personnelId);

            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("success", true);
            body.put("message", "Notifications loaded successfully.");
            body.put("count", notifications.size());
            body.put("unreadCount", unreadCount);
            body.put("notifications", notifications);

            ApiJson.send(response, HttpServletResponse.SC_OK, body);

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE,
                    "Notification listing failed for personnelId=" + personnelId,
                    exception);
            ApiJson.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load notifications.");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request,
                         HttpServletResponse response) throws IOException {

        if (!SessionUtil.isAuthenticated(request)) {
            ApiJson.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required.");
            return;
        }

        if (!CsrfUtil.isRequestTokenValid(request)) {
            ApiJson.error(response, HttpServletResponse.SC_FORBIDDEN,
                    "The request could not be verified.");
            return;
        }

        Integer personnelId =
                SessionUtil.getAuthenticatedPersonnelId(request);

        if (personnelId == null) {
            ApiJson.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid.");
            return;
        }

        JsonObject requestBody;
        try {
            requestBody = ApiJson.read(request, JsonObject.class);
        } catch (Exception exception) {
            ApiJson.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid request payload.");
            return;
        }

        String action = requestBody.has("action")
                ? requestBody.get("action").getAsString()
                : null;

        try {
            if ("markRead".equals(action)) {
                doMarkRead(response, personnelId, requestBody);
                return;
            }

            if ("markAllRead".equals(action)) {
                doMarkAllRead(response, personnelId);
                return;
            }

            ApiJson.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Unknown notification action.");
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE,
                    "Notification update failed for personnelId=" + personnelId,
                    exception);
            ApiJson.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to update notifications.");
        }
    }

    private void doMarkRead(HttpServletResponse response,
                            int personnelId,
                            JsonObject requestBody) throws Exception {

        long notificationId;
        try {
            if (!requestBody.has("notificationId")) {
                ApiJson.error(response, HttpServletResponse.SC_BAD_REQUEST,
                        "notificationId is required for markRead.");
                return;
            }

            long rawId = requestBody.get("notificationId").getAsLong();
            if (rawId <= 0) {
                ApiJson.error(response, HttpServletResponse.SC_BAD_REQUEST,
                        "notificationId must be a positive number.");
                return;
            }

            notificationId = rawId;
        } catch (Exception exception) {
            ApiJson.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "notificationId must be a positive number.");
            return;
        }

        int updated = notificationMutator.markRead(notificationId, personnelId);

        if (updated == 0) {
            Map<String, Object> existing = notificationReader.read(notificationId, personnelId);
            if (existing == null) {
                ApiJson.error(response, HttpServletResponse.SC_NOT_FOUND,
                        "The requested notification could not be found.");
                return;
            }
        }

        int unreadCount = unreadCountSupplier.count(personnelId);

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "Notification marked as read.");
        body.put("unreadCount", unreadCount);

        ApiJson.send(response, HttpServletResponse.SC_OK, body);
    }

    private void doMarkAllRead(HttpServletResponse response,
                               int personnelId) throws Exception {

        int updated = notificationAllMutator.markAllRead(personnelId);

        int unreadCount = unreadCountSupplier.count(personnelId);

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "Notifications marked as read.");
        body.put("updatedCount", updated);
        body.put("unreadCount", unreadCount);

        ApiJson.send(response, HttpServletResponse.SC_OK, body);
    }
}
