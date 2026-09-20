package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.WorkOrderHistoryDAO;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** HTTP boundary for the read-only administrator and technician work-order timeline. */
public final class WorkOrderHistoryServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(WorkOrderHistoryServlet.class.getName());
    private static final int ADMINISTRATOR_ROLE_ID = SessionUtil.SERVICE_ADMINISTRATOR_ROLE_ID;
    private static final int TECHNICIAN_ROLE_ID = SessionUtil.SERVICE_PERSONNEL_ROLE_ID;

    private final Gson gson = new Gson();

    @FunctionalInterface
    interface TimelineReader {
        WorkOrderHistoryDAO.LookupResult find(int actor, int role, long workOrderId) throws Exception;
    }

    private final TimelineReader timelineReader;

    public WorkOrderHistoryServlet() {
        WorkOrderHistoryDAO dao = new WorkOrderHistoryDAO();
        this.timelineReader = dao::findForActor;
    }

    WorkOrderHistoryServlet(TimelineReader timelineReader) {
        this.timelineReader = timelineReader;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepareJsonResponse(response);
        Integer actor = authenticatedActor(request, response);
        if (actor == null) return;

        Integer role = SessionUtil.getRoleId(request);
        if (role == null || (role != ADMINISTRATOR_ROLE_ID && role != TECHNICIAN_ROLE_ID)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Work-order history access is not permitted.");
            return;
        }

        Long workOrderId = parseRequiredWorkOrderId(request.getParameter("workOrderId"), response);
        if (workOrderId == null) return;

        try {
            WorkOrderHistoryDAO.LookupResult lookup = timelineReader.find(actor, role, workOrderId);
            if (!lookup.found()) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND,
                        "The requested work order was not found.");
                return;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Work-order history loaded successfully.");
            result.put("workOrderId", lookup.workOrderId());
            result.put("workOrderNumber", lookup.workOrderNumber());
            result.put("workOrderEvents", lookup.workOrderEvents());
            if (role == ADMINISTRATOR_ROLE_ID) result.put("requestEvents", lookup.requestEvents());
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result));
        } catch (SecurityException exception) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Work-order history access is not permitted.");
        } catch (IllegalArgumentException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The workOrderId parameter must be a positive whole number.");
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Work-order history retrieval failed.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load work-order history.");
        }
    }

    private Integer authenticatedActor(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!SessionUtil.isAuthenticated(request)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication is required.");
            return null;
        }
        Integer actor = SessionUtil.getAuthenticatedPersonnelId(request);
        if (actor == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid.");
            return null;
        }
        return actor;
    }

    private Long parseRequiredWorkOrderId(String value, HttpServletResponse response) throws IOException {
        if (value == null || value.isBlank()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The workOrderId parameter is required.");
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The workOrderId parameter must be a positive whole number.");
            return null;
        }
    }

    private void prepareJsonResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        response.getWriter().write(gson.toJson(result));
    }
}
