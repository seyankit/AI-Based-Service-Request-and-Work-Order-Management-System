package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.AuditLogDAO;
import ph.edu.htcgsc.serviceportal.util.ApiJson;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Administrator-only, read-only presentation endpoint for AUDIT_LOG. */
public final class AuditLogServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(AuditLogServlet.class.getName());
    private static final int ADMINISTRATOR_ROLE_ID = SessionUtil.SERVICE_ADMINISTRATOR_ROLE_ID;
    private static final Gson SUCCESS_RESPONSE_GSON = new GsonBuilder().serializeNulls().create();

    @FunctionalInterface
    interface AuditReader {
        AuditLogDAO.Page find(int actorId, AuditLogDAO.Filters filters,
                              AuditLogDAO.Cursor cursor, int limit) throws Exception;
    }

    private final AuditReader auditReader;

    public AuditLogServlet() {
        AuditLogDAO dao = new AuditLogDAO();
        this.auditReader = dao::findForAdministrator;
    }

    AuditLogServlet(AuditReader auditReader) {
        this.auditReader = auditReader;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!SessionUtil.isAuthenticated(request)) {
            ApiJson.error(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication is required.");
            return;
        }
        Integer actorId = SessionUtil.getAuthenticatedPersonnelId(request);
        if (actorId == null) {
            ApiJson.error(response, HttpServletResponse.SC_UNAUTHORIZED, "The authenticated session is invalid.");
            return;
        }
        Integer roleId = SessionUtil.getRoleId(request);
        if (roleId == null || roleId != ADMINISTRATOR_ROLE_ID) {
            ApiJson.error(response, HttpServletResponse.SC_FORBIDDEN,
                    "Service Administrator access is required.");
            return;
        }

        try {
            int limit = parseLimit(request.getParameter("limit"));
            AuditLogDAO.Filters filters = AuditLogDAO.parseFilters(
                    request.getParameter("actionType"), request.getParameter("outcome"),
                    request.getParameter("actorId"), request.getParameter("requestId"),
                    request.getParameter("workOrderId"), request.getParameter("from"),
                    request.getParameter("to"));
            AuditLogDAO.Cursor cursor = AuditLogDAO.parseCursor(request.getParameter("cursor"));
            AuditLogDAO.Page page = auditReader.find(actorId, filters, cursor, limit);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Audit records loaded successfully.");
            result.put("limit", limit);
            result.put("entries", page.entries());
            result.put("nextCursor", page.nextCursor());
            sendSuccess(response, result);
        } catch (SecurityException exception) {
            ApiJson.error(response, HttpServletResponse.SC_FORBIDDEN,
                    "Audit viewer access is not permitted.");
        } catch (IllegalArgumentException exception) {
            ApiJson.error(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Audit-log retrieval failed for administrator=" + actorId, exception);
            ApiJson.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load audit records.");
        }
    }

    private int parseLimit(String value) {
        if (value == null || value.isBlank()) return AuditLogDAO.DEFAULT_LIMIT;
        try {
            int limit = Integer.parseInt(value.trim());
            if (limit < 1 || limit > AuditLogDAO.MAXIMUM_LIMIT) throw new NumberFormatException();
            return limit;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("The limit parameter must be a whole number from 1 to "
                    + AuditLogDAO.MAXIMUM_LIMIT + ".");
        }
    }

    private void sendSuccess(HttpServletResponse response, Map<String, Object> result) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write(SUCCESS_RESPONSE_GSON.toJson(result));
    }
}
