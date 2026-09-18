package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.RequestStatusHistoryDAO;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestStatusHistoryServlet
        extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(
                    RequestStatusHistoryServlet.class.getName()
            );

    private final Gson gson =
            new Gson();

    private final RequestStatusHistoryDAO historyDAO =
            new RequestStatusHistoryDAO();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        prepareJsonResponse(response);

        if (!SessionUtil.isAuthenticated(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required."
            );

            return;
        }

        if (!SessionUtil.isRequester(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Only requester accounts may retrieve requester service-request history."
            );

            return;
        }

        Integer personnelId =
                SessionUtil.getAuthenticatedPersonnelId(
                        request
                );

        if (personnelId == null) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid."
            );

            return;
        }

        String requestIdParameter =
                request.getParameter("requestId");

        if (
            requestIdParameter == null
            || requestIdParameter.isBlank()
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "The requestId parameter is required."
            );

            return;
        }

        long requestId;

        try {
            requestId = Long.parseLong(
                    requestIdParameter.trim()
            );
        } catch (NumberFormatException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "The requestId parameter must be a positive whole number."
            );

            return;
        }

        if (requestId <= 0) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "The requestId parameter must be a positive whole number."
            );

            return;
        }

        try {
            RequestStatusHistoryDAO.LookupResult lookup =
                    historyDAO.findForRequester(
                            requestId,
                            personnelId
                    );

            if (!lookup.isRequestFound()) {
                sendError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "The requested service request was not found."
                );

                return;
            }

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Request status history loaded successfully."
            );

            result.put(
                    "requestId",
                    requestId
            );

            result.put(
                    "count",
                    lookup.getHistory().size()
            );

            result.put(
                    "history",
                    lookup.getHistory()
            );

            response.setStatus(
                    HttpServletResponse.SC_OK
            );

            response.getWriter().write(
                    gson.toJson(result)
            );

        } catch (SQLException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Request status-history retrieval failed.",
                    exception
            );

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load request status history."
            );
        }
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