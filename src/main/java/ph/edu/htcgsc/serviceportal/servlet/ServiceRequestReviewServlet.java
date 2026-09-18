package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.ServiceRequestReviewDAO;
import ph.edu.htcgsc.serviceportal.dao.ServiceRequestRoutingDAO;
import ph.edu.htcgsc.serviceportal.model.ServiceRequestReviewItem;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ServiceRequestReviewServlet
        extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(
                    ServiceRequestReviewServlet.class.getName()
            );

    private static final long MAXIMUM_BODY_LENGTH =
            8L * 1024L;

    private final Gson gson =
            new Gson();

    private final ServiceRequestReviewDAO reviewDAO =
            new ServiceRequestReviewDAO();

    private final ServiceRequestRoutingDAO routingDAO =
            new ServiceRequestRoutingDAO();

    private record ForwardRequest(
            Long requestId,
            Integer finalCategoryId,
            String finalPriority,
            Integer routedDepartmentId
    ) {
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        prepareJsonResponse(response);

        if (!requireServiceAdministrator(
                request,
                response
        )) {
            return;
        }

        int limit =
                ServiceRequestReviewDAO.DEFAULT_LIMIT;

        String limitParameter =
                request.getParameter("limit");

        if (
            limitParameter != null
            && !limitParameter.isBlank()
        ) {
            try {
                limit =
                        Integer.parseInt(
                                limitParameter.trim()
                        );

            } catch (NumberFormatException exception) {
                sendInvalidLimit(response);
                return;
            }

            if (
                limit < 1
                || limit >
                    ServiceRequestReviewDAO.MAXIMUM_LIMIT
            ) {
                sendInvalidLimit(response);
                return;
            }
        }

        try {
            List<ServiceRequestReviewItem> reviewItems =
                    reviewDAO.findSubmittedRequests(
                            limit
                    );

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Submitted service requests loaded successfully."
            );

            result.put(
                    "count",
                    reviewItems.size()
            );

            result.put(
                    "limit",
                    limit
            );

            result.put(
                    "reviewItems",
                    reviewItems
            );

            response.setStatus(
                    HttpServletResponse.SC_OK
            );

            response.getWriter().write(
                    gson.toJson(result)
            );

        } catch (IllegalArgumentException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Administrator service-request review retrieval failed.",
                    exception
            );

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load submitted service requests."
            );
        }
    }

    @Override
    protected void doPut(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        prepareJsonResponse(response);

        if (!requireServiceAdministrator(
                request,
                response
        )) {
            return;
        }

        if (!CsrfUtil.isRequestTokenValid(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "The security token is missing or invalid. Refresh the page and try again."
            );

            return;
        }

        String contentType =
                request.getContentType();

        if (
            contentType == null
            || !contentType
                    .toLowerCase()
                    .startsWith(
                            "application/json"
                    )
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be application/json."
            );

            return;
        }

        long contentLength =
                request.getContentLengthLong();

        if (
            contentLength >
            MAXIMUM_BODY_LENGTH
        ) {
            sendError(
                    response,
                    413,
                    "The forwarding request is too large."
            );

            return;
        }

        ForwardRequest forwardRequest;

        try {
            forwardRequest =
                    gson.fromJson(
                            request.getReader(),
                            ForwardRequest.class
                    );

        } catch (JsonSyntaxException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "The request contains invalid JSON."
            );

            return;
        }

        if (forwardRequest == null) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Forwarding data is required."
            );

            return;
        }

        if (
            forwardRequest.requestId() == null
            || forwardRequest.requestId() <= 0
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a valid service request."
            );

            return;
        }

        if (
            forwardRequest.finalCategoryId() == null
            || forwardRequest.finalCategoryId() <= 0
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a valid final service category."
            );

            return;
        }

        if (
            forwardRequest.finalPriority() == null
            || forwardRequest
                    .finalPriority()
                    .isBlank()
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a final priority."
            );

            return;
        }

        if (
            forwardRequest.routedDepartmentId() == null
            || forwardRequest.routedDepartmentId() <= 0
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a valid routed department."
            );

            return;
        }

        int actorPersonnelId =
                SessionUtil
                        .getAuthenticatedPersonnelId(
                                request
                        );

        int actorRoleId =
                SessionUtil.getRoleId(
                        request
                );

        try {
            ServiceRequestRoutingDAO.ForwardResult
                    forwardResult =
                    routingDAO.forwardForApproval(
                            actorPersonnelId,
                            actorRoleId,
                            forwardRequest.requestId(),
                            forwardRequest.finalCategoryId(),
                            forwardRequest.finalPriority(),
                            forwardRequest.routedDepartmentId(),
                            request.getRemoteAddr(),
                            request.getHeader(
                                    "User-Agent"
                            )
                    );

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Service request forwarded for department approval successfully."
            );

            result.put(
                    "forwardedRequest",
                    forwardResult
            );

            response.setStatus(
                    HttpServletResponse.SC_OK
            );

            response.getWriter().write(
                    gson.toJson(result)
            );

        } catch (IllegalArgumentException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (IllegalStateException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_CONFLICT,
                    exception.getMessage()
            );

        } catch (SecurityException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Administrator service-request forwarding failed.",
                    exception
            );

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to forward the service request for approval."
            );
        }
    }

    private boolean requireServiceAdministrator(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        if (!SessionUtil.isAuthenticated(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required."
            );

            return false;
        }

        if (
            !SessionUtil.hasRole(
                    request,
                    SessionUtil
                            .SERVICE_ADMINISTRATOR_ROLE_ID
            )
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Service Administrator access is required."
            );

            return false;
        }

        return true;
    }

    private void sendInvalidLimit(
            HttpServletResponse response
    ) throws IOException {

        sendError(
                response,
                HttpServletResponse.SC_BAD_REQUEST,
                "The limit parameter must be a whole number from 1 to "
                        + ServiceRequestReviewDAO.MAXIMUM_LIMIT
                        + "."
        );
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        response.setStatus(status);

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "success",
                false
        );

        result.put(
                "message",
                message
        );

        response.getWriter().write(
                gson.toJson(result)
        );
    }

    private void prepareJsonResponse(
            HttpServletResponse response
    ) {
        response.setContentType(
                "application/json"
        );

        response.setCharacterEncoding(
                "UTF-8"
        );

        response.setHeader(
                "Cache-Control",
                "no-store"
        );

        response.setHeader(
                "X-Content-Type-Options",
                "nosniff"
        );
    }
}