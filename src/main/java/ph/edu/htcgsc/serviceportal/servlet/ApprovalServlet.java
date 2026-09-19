package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.ApprovalDAO;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ApprovalServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(
            ApprovalServlet.class.getName());

    private static final long MAXIMUM_BODY_LENGTH = 8L * 1024L;

    private static final Set<String> DECISIONS =
            Set.of("Approved", "Rejected");

   private final Gson gson = new Gson();

@FunctionalInterface
interface ApprovalLister {
    List<Map<String, Object>> list(
            int actor,
            int role,
            String status
    ) throws Exception;
}

@FunctionalInterface
interface ApprovalDecider {
    void decide(
            int actor,
            int role,
            long approvalId,
            String decision,
            String remarks
    ) throws Exception;
}

private final ApprovalLister approvalLister;
private final ApprovalDecider approvalDecider;

public ApprovalServlet() {
    ApprovalDAO approvalDAO = new ApprovalDAO();
    this.approvalLister = approvalDAO::list;
    this.approvalDecider = approvalDAO::decide;
}

ApprovalServlet(
        ApprovalLister approvalLister,
        ApprovalDecider approvalDecider
) {
    this.approvalLister = approvalLister;
    this.approvalDecider = approvalDecider;
}

    private record DecisionRequest(
            Long approvalId,
            String decision,
            String remarks
    ) {
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        prepareJsonResponse(response);

        Integer actorId = authenticatedActor(request, response);
        if (actorId == null) {
            return;
        }

        Integer roleId = SessionUtil.getRoleId(request);
        if (roleId == null || (roleId != 2 && roleId != 3)) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Approval review access is required.");
            return;
        }

        String status = request.getParameter("status");
        if (status == null) {
            status = "";
        } else {
            status = status.trim();
        }

        if (!status.isEmpty() && !DECISIONS.contains(status)
                && !"Pending".equals(status)) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Status must be Pending, Approved, or Rejected.");
            return;
        }

        try {
            List<Map<String, Object>> approvals = approvalLister.list(
                    actorId,
                    roleId,
                    status);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Approval records loaded successfully.");
            result.put("count", approvals.size());
            result.put("approvals", approvals);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result));

        } catch (SecurityException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Approval review access is required.");

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Approval retrieval failed.", exception);
            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load approval records.");
        }
    }

    @Override
    protected void doPut(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        prepareJsonResponse(response);

        Integer actorId = authenticatedActor(request, response);
        if (actorId == null) {
            return;
        }

        Integer roleId = SessionUtil.getRoleId(request);
        if (roleId == null || roleId != SessionUtil.DEPARTMENT_HEAD_ROLE_ID) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Department Head approval access is required.");
            return;
        }

        if (!CsrfUtil.isRequestTokenValid(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "The security token is missing or invalid. Refresh the page and try again.");
            return;
        }

        String contentType = request.getContentType();
        if (contentType == null
                || !contentType.toLowerCase().startsWith("application/json")) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be application/json.");
            return;
        }

        if (request.getContentLengthLong() > MAXIMUM_BODY_LENGTH) {
            sendError(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The approval request is too large.");
            return;
        }

        DecisionRequest decisionRequest;
        try {
            decisionRequest = gson.fromJson(
                    request.getReader(),
                    DecisionRequest.class);
        } catch (JsonParseException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "The request contains invalid JSON.");
            return;
        }

        if (decisionRequest == null
                || decisionRequest.approvalId() == null
                || decisionRequest.approvalId() <= 0) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "A valid approval ID is required.");
            return;
        }

        if (decisionRequest.decision() == null
                || !DECISIONS.contains(decisionRequest.decision())) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Decision must be Approved or Rejected.");
            return;
        }

        String remarks = decisionRequest.remarks() == null
                ? ""
                : decisionRequest.remarks().trim();
        if (remarks.length() < 3 || remarks.length() > 1000) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Decision remarks must contain 3 to 1000 characters.");
            return;
        }

        try {
            approvalDecider.decide(
                    actorId,
                    roleId,
                    decisionRequest.approvalId(),
                    decisionRequest.decision(),
                    remarks);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Approval decision recorded successfully.");
            result.put("approvalId", decisionRequest.approvalId());
            result.put("decision", decisionRequest.decision());

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result));

        } catch (IllegalArgumentException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage());

        } catch (IllegalStateException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_CONFLICT,
                    exception.getMessage());

        } catch (SecurityException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Approval decision access is not permitted.");

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Approval decision failed.", exception);
            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to record the approval decision.");
        }
    }

    private Integer authenticatedActor(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        if (!SessionUtil.isAuthenticated(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required.");
            return null;
        }

        Integer actorId = SessionUtil.getAuthenticatedPersonnelId(request);
        if (actorId == null) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid.");
            return null;
        }

        return actorId;
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {
        response.setStatus(status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        response.getWriter().write(gson.toJson(result));
    }

    private void prepareJsonResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
