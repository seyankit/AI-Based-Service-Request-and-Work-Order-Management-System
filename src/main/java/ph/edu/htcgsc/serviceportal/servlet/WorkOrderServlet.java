package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.WorkOrderDAO;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorkOrderServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(WorkOrderServlet.class.getName());
    private static final long MAXIMUM_BODY_LENGTH = 8L * 1024L;
    private static final Pattern REQUEST_NUMBER = Pattern.compile("SR-\\d{4}-\\d{6}");
    private static final int ADMINISTRATOR_ROLE_ID = SessionUtil.SERVICE_ADMINISTRATOR_ROLE_ID;

    private final Gson gson = new Gson();

    @FunctionalInterface
    interface WorkOrderLister {
        List<Map<String, Object>> list(int actor, int role, Long workOrderId) throws Exception;
    }

    @FunctionalInterface
    interface WorkOrderCreator {
        Map<String, Object> create(int actor, int role, WorkOrderDAO.CreateInput input)
                throws Exception;
    }

    private final WorkOrderLister workOrderLister;
    private final WorkOrderCreator workOrderCreator;

    public WorkOrderServlet() {
        WorkOrderDAO workOrderDAO = new WorkOrderDAO();
        this.workOrderLister = workOrderDAO::list;
        this.workOrderCreator = workOrderDAO::create;
    }

    WorkOrderServlet(WorkOrderLister workOrderLister, WorkOrderCreator workOrderCreator) {
        this.workOrderLister = workOrderLister;
        this.workOrderCreator = workOrderCreator;
    }

    private record CreateRequest(
            Long requestId,
            String requestNumber,
            String workDescription,
            String targetCompletionDate
    ) { }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepareJsonResponse(response);
        Integer actor = authenticatedActor(request, response);
        if (actor == null) return;

        Integer role = SessionUtil.getRoleId(request);
        if (role == null || role != ADMINISTRATOR_ROLE_ID) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Service Administrator access is required.");
            return;
        }

        Long workOrderId = parseWorkOrderId(request.getParameter("workOrderId"), response);
        if (request.getParameter("workOrderId") != null && workOrderId == null) return;

        try {
            List<Map<String, Object>> workOrders = workOrderLister.list(actor, role, workOrderId);
            if (workOrderId != null && workOrders.isEmpty()) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND,
                        "The requested work order was not found.");
                return;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Work orders loaded successfully.");
            result.put("count", workOrders.size());
            result.put("workOrders", workOrders);
            if (workOrderId != null) result.put("workOrder", workOrders.get(0));
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result));
        } catch (SecurityException exception) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Service Administrator access is required.");
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Work-order retrieval failed.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load work orders.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepareJsonResponse(response);
        Integer actor = authenticatedActor(request, response);
        if (actor == null) return;

        Integer role = SessionUtil.getRoleId(request);
        if (role == null || role != ADMINISTRATOR_ROLE_ID) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Service Administrator access is required.");
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
                    "The work-order request is too large.");
            return;
        }

        CreateRequest body;
        try {
            body = gson.fromJson(request.getReader(), CreateRequest.class);
        } catch (JsonParseException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON request.");
            return;
        }
        if (body == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "A JSON request body is required.");
            return;
        }
        if (body.requestId() != null && body.requestId() <= 0) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The request ID must be positive.");
            return;
        }

        String requestNumber = body.requestNumber() == null
                ? ""
                : body.requestNumber().trim().toUpperCase();
        if (body.requestId() == null
                && (requestNumber.isEmpty() || !REQUEST_NUMBER.matcher(requestNumber).matches())) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "A valid request ID or request number is required.");
            return;
        }
        if (body.requestId() != null && !requestNumber.isEmpty()
                && !REQUEST_NUMBER.matcher(requestNumber).matches()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The request number is invalid.");
            return;
        }

        String description = body.workDescription() == null
                ? ""
                : body.workDescription().trim();
        if (description.length() < 10 || description.length() > 2000) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Work description must contain 10 to 2000 characters.");
            return;
        }

        LocalDate targetCompletionDate = null;
        if (body.targetCompletionDate() != null && !body.targetCompletionDate().isBlank()) {
            try {
                targetCompletionDate = LocalDate.parse(body.targetCompletionDate().trim());
            } catch (DateTimeParseException exception) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "The target completion date must use YYYY-MM-DD format.");
                return;
            }
        }

        try {
            Map<String, Object> workOrder = workOrderCreator.create(
                    actor,
                    role,
                    new WorkOrderDAO.CreateInput(
                            body.requestId(),
                            requestNumber.isEmpty() ? null : requestNumber,
                            description,
                            targetCompletionDate));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Work order created successfully.");
            result.put("workOrder", workOrder);
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(gson.toJson(result));
        } catch (WorkOrderDAO.CreationException exception) {
            int status = switch (exception.failure()) {
                case REQUEST_NOT_FOUND -> HttpServletResponse.SC_NOT_FOUND;
                case REQUEST_NOT_APPROVED, REQUEST_NOT_ROUTED, DUPLICATE_WORK_ORDER ->
                        HttpServletResponse.SC_CONFLICT;
            };
            sendError(response, status, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (SecurityException exception) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Work-order creation access is not permitted.");
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Work-order creation failed.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to create the work order.");
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
