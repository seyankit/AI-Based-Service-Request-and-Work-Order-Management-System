package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.WorkOrderProgressDAO;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** HTTP boundary for the authenticated technician work-order workflow. */
public final class WorkOrderProgressServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(WorkOrderProgressServlet.class.getName());
    private static final long MAXIMUM_BODY_LENGTH = 8L * 1024L;
    private static final int TECHNICIAN_ROLE_ID = SessionUtil.SERVICE_PERSONNEL_ROLE_ID;

    private final Gson gson = new Gson();

    @FunctionalInterface
    interface WorkOrderReader {
        List<Map<String, Object>> list(int actor, int role, Long workOrderId) throws Exception;
    }

    @FunctionalInterface
    interface WorkOrderMutator {
        Map<String, Object> mutate(int actor, int role, WorkOrderProgressDAO.MutationInput input)
                throws Exception;
    }

    private final WorkOrderReader workOrderReader;
    private final WorkOrderMutator workOrderMutator;

    public WorkOrderProgressServlet() {
        WorkOrderProgressDAO dao = new WorkOrderProgressDAO();
        this.workOrderReader = dao::list;
        this.workOrderMutator = dao::mutate;
    }

    WorkOrderProgressServlet(WorkOrderReader reader, WorkOrderMutator mutator) {
        this.workOrderReader = reader;
        this.workOrderMutator = mutator;
    }

    private record ProgressRequest(
            String action,
            Long workOrderId,
            Integer progressPercentage,
            String progressNotes,
            String completionSummary
    ) { }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepareJsonResponse(response);
        Integer actor = authenticatedActor(request, response);
        if (actor == null) return;
        Integer role = SessionUtil.getRoleId(request);
        if (role == null || role != TECHNICIAN_ROLE_ID) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Service Technician access is required.");
            return;
        }

        Long workOrderId = parseWorkOrderId(request.getParameter("workOrderId"), response);
        if (request.getParameter("workOrderId") != null && workOrderId == null) return;
        try {
            List<Map<String, Object>> workOrders = workOrderReader.list(actor, role, workOrderId);
            if (workOrderId != null && workOrders.isEmpty()) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND,
                        "The requested work order was not found.");
                return;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Technician work orders loaded successfully.");
            result.put("count", workOrders.size());
            result.put("workOrders", workOrders);
            if (workOrderId != null) result.put("workOrder", workOrders.get(0));
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result));
        } catch (SecurityException exception) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Service Technician access is required.");
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Technician work-order retrieval failed.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load technician work orders.");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepareJsonResponse(response);
        Integer actor = authenticatedActor(request, response);
        if (actor == null) return;
        Integer role = SessionUtil.getRoleId(request);
        if (role == null || role != TECHNICIAN_ROLE_ID) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Service Technician access is required.");
            return;
        }
        if (!CsrfUtil.isRequestTokenValid(request)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "The security token is missing or invalid. Refresh the page and try again.");
            return;
        }
        if (!isJsonRequest(request)) {
            sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be application/json.");
            return;
        }
        if (request.getContentLengthLong() > MAXIMUM_BODY_LENGTH) {
            sendError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The progress request is too large.");
            return;
        }

        ProgressRequest body;
        try {
            body = gson.fromJson(request.getReader(), ProgressRequest.class);
        } catch (JsonParseException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON request.");
            return;
        }
        if (body == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "A JSON request body is required.");
            return;
        }
        String action = body.action() == null ? "" : body.action().trim();
        if (!List.of("acknowledge", "start", "progress", "hold", "resume", "complete")
                .contains(action)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Unknown work-order action.");
            return;
        }
        if (body.workOrderId() == null || body.workOrderId() <= 0) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The work-order ID must be positive.");
            return;
        }
        String notes = body.progressNotes() == null ? "" : body.progressNotes().trim();
        if (notes.length() < 5 || notes.length() > 2000) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Progress notes must contain 5 to 2000 characters.");
            return;
        }
        if ("progress".equals(action)
                && (body.progressPercentage() == null
                || body.progressPercentage() < 1
                || body.progressPercentage() > 99)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Progress updates must be from 1 to 99 percent.");
            return;
        }
        String summary = body.completionSummary() == null ? "" : body.completionSummary().trim();
        if ("complete".equals(action) && summary.length() < 10) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Completion summary must contain at least 10 characters.");
            return;
        }

        try {
            Map<String, Object> workOrder = workOrderMutator.mutate(actor, role,
                    new WorkOrderProgressDAO.MutationInput(action, body.workOrderId(),
                            body.progressPercentage(), notes,
                            "complete".equals(action) ? summary : null));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Work-order progress recorded successfully.");
            result.put("workOrder", workOrder);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result));
        } catch (WorkOrderProgressDAO.ProgressException exception) {
            int status = switch (exception.failure()) {
                case WORK_ORDER_NOT_FOUND, ASSIGNMENT_NOT_FOUND -> HttpServletResponse.SC_NOT_FOUND;
                case NOT_CURRENT_ASSIGNEE -> HttpServletResponse.SC_FORBIDDEN;
                case STALE_STATE -> HttpServletResponse.SC_CONFLICT;
            };
            sendError(response, status, exception.getMessage());
        } catch (IllegalStateException exception) {
            sendError(response, HttpServletResponse.SC_CONFLICT, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (SecurityException exception) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Service Technician access is not permitted.");
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Technician work-order progress failed.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to record work-order progress.");
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

    private Long parseWorkOrderId(String value, HttpServletResponse response) throws IOException {
        if (value == null || value.isBlank()) return null;
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

    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json");
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
