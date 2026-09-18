package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import ph.edu.htcgsc.serviceportal.dao.ServiceRequestDAO;
import ph.edu.htcgsc.serviceportal.dto.CreateServiceRequestRequest;
import ph.edu.htcgsc.serviceportal.model.ServiceRequest;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.ServiceRequestValidator;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;
import ph.edu.htcgsc.serviceportal.dao.ServiceRequestQueryDAO;

import java.io.IOException;
import java.io.Reader;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;

public class ServiceRequestServlet extends HttpServlet {
    private final ServiceRequestQueryDAO serviceRequestQueryDAO = new ServiceRequestQueryDAO();

    private static final Logger LOGGER = Logger.getLogger(
            ServiceRequestServlet.class.getName());

    private static final int MAXIMUM_JSON_CHARACTERS = 32_768;

    private final Gson gson = new Gson();

    private final ServiceRequestDAO serviceRequestDAO = new ServiceRequestDAO();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        prepareJsonResponse(response);

        if (!SessionUtil.isAuthenticated(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required.");

            return;
        }

        if (!SessionUtil.isRequester(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Only requester accounts may retrieve requester service requests.");

            return;
        }

        Integer personnelId = SessionUtil.getAuthenticatedPersonnelId(
                request);

        if (personnelId == null) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid.");

            return;
        }

        String requestIdParameter = request.getParameter("requestId");

        if (requestIdParameter != null) {
            long requestId;

            try {
                requestId = Long.parseLong(requestIdParameter.trim());
            } catch (NumberFormatException exception) {
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "The requestId parameter must be a positive whole number.");

                return;
            }

            if (requestId <= 0) {
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "The requestId parameter must be a positive whole number.");

                return;
            }

            try {
                ServiceRequest serviceRequest = serviceRequestQueryDAO
                        .findByRequesterIdAndRequestId(
                                personnelId,
                                requestId);

                if (serviceRequest == null) {
                    sendError(
                            response,
                            HttpServletResponse.SC_NOT_FOUND,
                            "The requested service request was not found.");

                    return;
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("message", "Service request loaded successfully.");
                result.put("request", serviceRequest);

                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(gson.toJson(result));
                return;

            } catch (SQLException exception) {
                LOGGER.log(
                        Level.SEVERE,
                        "Requester service-request detail retrieval failed.",
                        exception);

                sendError(
                        response,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Unable to load the service request.");

                return;
            }
        }

        int limit = ServiceRequestQueryDAO.DEFAULT_LIMIT;

        String limitParameter = request.getParameter("limit");

        if (limitParameter != null
                && !limitParameter.isBlank()) {
            try {
                limit = Integer.parseInt(
                        limitParameter.trim());
            } catch (NumberFormatException exception) {
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "The limit parameter must be a whole number from 1 to 100.");

                return;
            }

            if (limit < 1
                    || limit > ServiceRequestQueryDAO.MAXIMUM_LIMIT) {
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "The limit parameter must be a whole number from 1 to 100.");

                return;
            }
        }

        try {
            List<ServiceRequest> requests = serviceRequestQueryDAO
                    .findByRequesterId(
                            personnelId,
                            limit);

            Map<String, Object> result = new LinkedHashMap<>();

            result.put(
                    "success",
                    true);

            result.put(
                    "message",
                    "Service requests loaded successfully.");

            result.put(
                    "count",
                    requests.size());

            result.put(
                    "limit",
                    limit);

            result.put(
                    "requests",
                    requests);

            response.setStatus(
                    HttpServletResponse.SC_OK);

            response.getWriter().write(
                    gson.toJson(result));

        } catch (SQLException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Requester service-request retrieval failed.",
                    exception);

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load service requests.");
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        prepareJsonResponse(response);

        request.setCharacterEncoding("UTF-8");

        if (!SessionUtil.isAuthenticated(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required.");

            return;
        }

        if (!SessionUtil.isRequester(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Only authorized requester accounts may submit service requests.");

            return;
        }

        if (!CsrfUtil.isRequestTokenValid(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "The security token is missing or invalid. Refresh the page and try again.");

            return;
        }

        if (!isJsonRequest(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be application/json.");

            return;
        }

        long contentLength = request.getContentLengthLong();

        if (contentLength > MAXIMUM_JSON_CHARACTERS) {
            sendError(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The request body is too large.");

            return;
        }

        Integer personnelId = SessionUtil.getAuthenticatedPersonnelId(
                request);

        if (personnelId == null) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid.");

            return;
        }

        try {
            String jsonBody = readLimitedBody(request);

            if (jsonBody.isBlank()) {
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "A JSON request body is required.");

                return;
            }

            CreateServiceRequestRequest requestData = gson.fromJson(
                    jsonBody,
                    CreateServiceRequestRequest.class);

            Map<String, String> validationErrors = ServiceRequestValidator
                    .validateAndNormalize(
                            requestData);

            if (!validationErrors.isEmpty()) {
                sendValidationError(
                        response,
                        validationErrors);

                return;
            }

            ServiceRequest createdRequest = serviceRequestDAO
                    .createServiceRequest(
                            requestData,
                            personnelId,
                            request.getRemoteAddr(),
                            request.getHeader(
                                    "User-Agent"));

            Map<String, Object> result = new LinkedHashMap<>();

            result.put(
                    "success",
                    true);

            result.put(
                    "message",
                    "Service request submitted successfully.");

            result.put(
                    "request",
                    createdRequest);

            result.put(
                    "aiAnalysisStatus",
                    "Pending");

            result.put(
                    "aiAdvisoryNotice",
                    "AI recommendations require review by authorized school personnel.");

            response.setStatus(
                    HttpServletResponse.SC_CREATED);

            response.getWriter().write(
                    gson.toJson(result));

        } catch (PayloadTooLargeException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The request body is too large.");

        } catch (JsonParseException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Malformed JSON request.");

        } catch (ServiceRequestDAO.CreationException exception) {
            handleCreationException(
                    request,
                    response,
                    exception);

        } catch (SQLException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Service request database transaction failed.",
                    exception);

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to submit the service request.");

        } catch (Exception exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Service request submission failed.",
                    exception);

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to submit the service request.");
        }
    }

    private String readLimitedBody(
            HttpServletRequest request) throws IOException, PayloadTooLargeException {

        StringBuilder body = new StringBuilder();

        char[] buffer = new char[2048];

        try (Reader reader = request.getReader()) {
            int charactersRead;

            while ((charactersRead = reader.read(buffer)) != -1) {
                if (body.length() + charactersRead > MAXIMUM_JSON_CHARACTERS) {
                    throw new PayloadTooLargeException();
                }

                body.append(
                        buffer,
                        0,
                        charactersRead);
            }
        }

        return body.toString();
    }

    private void handleCreationException(
            HttpServletRequest request,
            HttpServletResponse response,
            ServiceRequestDAO.CreationException exception) throws IOException {

        switch (exception.getReason()) {
            case CATEGORY_NOT_AVAILABLE ->
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        exception.getMessage());

            case PERSONNEL_NOT_FOUND -> {
                invalidateSession(request);

                sendError(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "The authenticated personnel account is no longer available.");
            }

            case ACCOUNT_NOT_ACTIVE ->
                sendError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "The personnel account is not active.");

            case REQUESTER_ROLE_REQUIRED ->
                sendError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "Only authorized requester accounts may submit service requests.");

            case MULTIPLE_ROLE_ASSIGNMENTS -> {
                LOGGER.warning(
                        "Request submission denied because the account has multiple role assignments.");

                sendError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "The account authorization could not be verified.");
            }
        }
    }

    private void invalidateSession(
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return;
        }

        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // The session was already invalidated.
        }
    }

    private boolean isJsonRequest(
            HttpServletRequest request) {
        String contentType = request.getContentType();

        return contentType != null
                && contentType
                        .toLowerCase(Locale.ROOT)
                        .startsWith(
                                "application/json");
    }

    private void sendValidationError(
            HttpServletResponse response,
            Map<String, String> validationErrors) throws IOException {

        response.setStatus(
                HttpServletResponse.SC_BAD_REQUEST);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put(
                "success",
                false);

        result.put(
                "message",
                "Correct the highlighted request fields.");

        result.put(
                "errors",
                validationErrors);

        response.getWriter().write(
                gson.toJson(result));
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String message) throws IOException {

        response.setStatus(status);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put(
                "success",
                false);

        result.put(
                "message",
                message);

        response.getWriter().write(
                gson.toJson(result));
    }

    private void prepareJsonResponse(
            HttpServletResponse response) {
        response.setContentType(
                "application/json");

        response.setCharacterEncoding(
                "UTF-8");

        response.setHeader(
                "Cache-Control",
                "no-store");

        response.setHeader(
                "X-Content-Type-Options",
                "nosniff");
    }

    private static final class PayloadTooLargeException
            extends Exception {
    }
}