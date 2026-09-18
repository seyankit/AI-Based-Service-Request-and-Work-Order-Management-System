package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.ServiceRequestDuplicateDAO;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ServiceRequestDuplicateServlet
        extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(
                    ServiceRequestDuplicateServlet.class.getName()
            );

    private static final long MAXIMUM_BODY_LENGTH =
            8L * 1024L;

    private final Gson gson =
            new Gson();

    private final ServiceRequestDuplicateDAO duplicateDAO =
            new ServiceRequestDuplicateDAO();

    private record DuplicateRequest(
            Long duplicateRequestId,
            Long originalRequestId,
            String confirmationReason
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

        String requestIdParameter =
                request.getParameter(
                        "duplicateRequestId"
                );

        long duplicateRequestId;

        try {
            duplicateRequestId =
                    Long.parseLong(
                            String.valueOf(
                                    requestIdParameter
                            ).trim()
                    );

        } catch (NumberFormatException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "A valid duplicateRequestId is required."
            );

            return;
        }

        if (duplicateRequestId <= 0) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "A valid duplicateRequestId is required."
            );

            return;
        }

        try {
            java.util.List<
                    ServiceRequestDuplicateDAO
                            .OriginalRequestCandidate
                    > candidates =
                    duplicateDAO
                            .findOriginalRequestCandidates(
                                    duplicateRequestId
                            );

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Original-request candidates loaded successfully."
            );

            result.put(
                    "count",
                    candidates.size()
            );

            result.put(
                    "candidates",
                    candidates
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
                    "Duplicate candidate retrieval failed.",
                    exception
            );

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load possible original requests."
            );
        }
    }

    @Override
    protected void doPost(
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
                    "The duplicate confirmation request is too large."
            );

            return;
        }

        DuplicateRequest duplicateRequest;

        try {
            duplicateRequest =
                    gson.fromJson(
                            request.getReader(),
                            DuplicateRequest.class
                    );

        } catch (JsonSyntaxException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "The request contains invalid JSON."
            );

            return;
        }

        if (duplicateRequest == null) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Duplicate confirmation data is required."
            );

            return;
        }

        if (
            duplicateRequest.duplicateRequestId() == null
            || duplicateRequest.duplicateRequestId() <= 0
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a valid duplicate request."
            );

            return;
        }

        if (
            duplicateRequest.originalRequestId() == null
            || duplicateRequest.originalRequestId() <= 0
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a valid original request."
            );

            return;
        }

        if (
            duplicateRequest.confirmationReason() == null
            || duplicateRequest
                    .confirmationReason()
                    .trim()
                    .length() < 5
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "The duplicate confirmation reason must contain at least 5 characters."
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
            ServiceRequestDuplicateDAO.DuplicateResult
                    duplicateResult =
                    duplicateDAO.markDuplicate(
                            actorPersonnelId,
                            actorRoleId,
                            duplicateRequest
                                    .duplicateRequestId(),
                            duplicateRequest
                                    .originalRequestId(),
                            duplicateRequest
                                    .confirmationReason(),
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
                    "Service request marked as duplicate successfully."
            );

            result.put(
                    "duplicate",
                    duplicateResult
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
                    "Administrator duplicate confirmation failed.",
                    exception
            );

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to mark the service request as duplicate."
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