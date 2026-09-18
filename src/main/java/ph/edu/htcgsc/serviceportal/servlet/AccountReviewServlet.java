package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import ph.edu.htcgsc.serviceportal.dao.AccountReviewDAO;
import ph.edu.htcgsc.serviceportal.dao.AccountReviewDecisionDAO;
import ph.edu.htcgsc.serviceportal.model.AccountReview;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.io.Reader;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AccountReviewServlet
        extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(
                    AccountReviewServlet.class.getName()
            );

    private static final int MAXIMUM_JSON_CHARACTERS =
            16_384;

    private final Gson gson =
            new Gson();

    private final AccountReviewDAO accountReviewDAO =
            new AccountReviewDAO();

    private final AccountReviewDecisionDAO
            accountReviewDecisionDAO =
            new AccountReviewDecisionDAO();

    private static final class DecisionRequest {
        Long accountReviewId;
        String decision;
        String remarks;
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
                AccountReviewDAO.DEFAULT_LIMIT;

        String limitParameter =
                request.getParameter("limit");

        if (
            limitParameter != null
            && !limitParameter.isBlank()
        ) {
            try {
                limit = Integer.parseInt(
                        limitParameter.trim()
                );
            } catch (NumberFormatException exception) {
                sendInvalidLimit(response);
                return;
            }

            if (
                limit < 1
                || limit
                    > AccountReviewDAO.MAXIMUM_LIMIT
            ) {
                sendInvalidLimit(response);
                return;
            }
        }

        try {
            List<AccountReview> reviews =
                    accountReviewDAO
                            .findPendingReviews(limit);

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Pending account reviews loaded successfully."
            );

            result.put(
                    "count",
                    reviews.size()
            );

            result.put(
                    "limit",
                    limit
            );

            result.put(
                    "reviews",
                    reviews
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
                    "Pending account-review retrieval failed.",
                    exception
            );

            sendError(
                    response,
                    HttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR,
                    "Unable to load pending account reviews."
            );
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        prepareJsonResponse(response);

        request.setCharacterEncoding("UTF-8");

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

        if (!isJsonRequest(request)) {
            sendError(
                    response,
                    HttpServletResponse
                            .SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be application/json."
            );

            return;
        }

        long contentLength =
                request.getContentLengthLong();

        if (
            contentLength
            > MAXIMUM_JSON_CHARACTERS
        ) {
            sendError(
                    response,
                    HttpServletResponse
                            .SC_REQUEST_ENTITY_TOO_LARGE,
                    "The request body is too large."
            );

            return;
        }

        Integer reviewerPersonnelId =
                SessionUtil
                        .getAuthenticatedPersonnelId(
                                request
                        );

        if (reviewerPersonnelId == null) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid."
            );

            return;
        }

        try {
            String jsonBody =
                    readLimitedBody(request);

            if (jsonBody.isBlank()) {
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "A JSON request body is required."
                );

                return;
            }

            DecisionRequest decisionRequest =
                    gson.fromJson(
                            jsonBody,
                            DecisionRequest.class
                    );

            if (
                decisionRequest == null
                || decisionRequest.accountReviewId == null
                || decisionRequest.accountReviewId <= 0
            ) {
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "A valid accountReviewId is required."
                );

                return;
            }

            String normalizedDecision =
                    normalizeDecision(
                            decisionRequest.decision
                    );

            if (normalizedDecision == null) {
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "The decision must be Approved or Rejected."
                );

                return;
            }

            String normalizedRemarks =
                    normalizeRemarks(
                            decisionRequest.remarks
                    );

            if (
                normalizedRemarks == null
                || normalizedRemarks.length() < 5
                || normalizedRemarks.length() > 1000
            ) {
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Decision remarks must contain 5 to 1000 characters."
                );

                return;
            }

            AccountReviewDecisionDAO.DecisionResult
                    decisionResult =
                    accountReviewDecisionDAO
                            .decideReview(
                                    decisionRequest
                                            .accountReviewId,
                                    normalizedDecision,
                                    normalizedRemarks,
                                    reviewerPersonnelId,
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
                    "Approved".equals(
                            decisionResult.reviewStatus()
                    )
                            ? "Account approved successfully."
                            : "Account registration rejected successfully."
            );

            result.put(
                    "accountReviewId",
                    decisionResult.accountReviewId()
            );

            result.put(
                    "personnelId",
                    decisionResult.personnelId()
            );

            result.put(
                    "reviewStatus",
                    decisionResult.reviewStatus()
            );

            result.put(
                    "accountStatus",
                    decisionResult.accountStatus()
            );

            result.put(
                    "decisionRemarks",
                    decisionResult.decisionRemarks()
            );

            response.setStatus(
                    HttpServletResponse.SC_OK
            );

            response.getWriter().write(
                    gson.toJson(result)
            );

        } catch (PayloadTooLargeException exception) {
            sendError(
                    response,
                    HttpServletResponse
                            .SC_REQUEST_ENTITY_TOO_LARGE,
                    "The request body is too large."
            );

        } catch (JsonParseException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Malformed JSON request."
            );

        } catch (
                AccountReviewDecisionDAO.DecisionException
                        exception
        ) {
            handleDecisionException(
                    request,
                    response,
                    exception
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
                    "Account-review decision transaction failed.",
                    exception
            );

            sendError(
                    response,
                    HttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR,
                    "Unable to process the account review."
            );

        } catch (Exception exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Account-review decision failed.",
                    exception
            );

            sendError(
                    response,
                    HttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR,
                    "Unable to process the account review."
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

    private void handleDecisionException(
            HttpServletRequest request,
            HttpServletResponse response,
            AccountReviewDecisionDAO.DecisionException
                    exception
    ) throws IOException {

        switch (exception.getReason()) {
            case REVIEW_NOT_FOUND ->
                    sendError(
                            response,
                            HttpServletResponse.SC_NOT_FOUND,
                            "The account review was not found."
                    );

            case REVIEW_ALREADY_DECIDED ->
                    sendError(
                            response,
                            HttpServletResponse.SC_CONFLICT,
                            "The account review has already been decided."
                    );

            case SELF_REVIEW_NOT_ALLOWED ->
                    sendError(
                            response,
                            HttpServletResponse.SC_FORBIDDEN,
                            "A reviewer cannot decide their own account review."
                    );

            case REVIEWER_NOT_AUTHORIZED -> {
                invalidateSession(request);

                sendError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "Service Administrator authorization is no longer valid."
                );
            }

            case ACCOUNT_STATE_INVALID,
                 ACCOUNT_ASSIGNMENT_INVALID -> {
                LOGGER.warning(
                        "Account review "
                                + exception.getReason()
                                + ": "
                                + exception.getMessage()
                );

                sendError(
                        response,
                        HttpServletResponse.SC_CONFLICT,
                        "The account review cannot be processed because its account state is inconsistent."
                );
            }
        }
    }

    private String readLimitedBody(
            HttpServletRequest request
    ) throws IOException, PayloadTooLargeException {

        StringBuilder body =
                new StringBuilder();

        char[] buffer =
                new char[2048];

        try (Reader reader = request.getReader()) {
            int charactersRead;

            while (
                (charactersRead =
                        reader.read(buffer)) != -1
            ) {
                if (
                    body.length() + charactersRead
                    > MAXIMUM_JSON_CHARACTERS
                ) {
                    throw new PayloadTooLargeException();
                }

                body.append(
                        buffer,
                        0,
                        charactersRead
                );
            }
        }

        return body.toString();
    }

    private String normalizeDecision(
            String decision
    ) {
        if (decision == null) {
            return null;
        }

        String trimmed =
                decision.trim();

        if ("Approved".equalsIgnoreCase(trimmed)) {
            return "Approved";
        }

        if ("Rejected".equalsIgnoreCase(trimmed)) {
            return "Rejected";
        }

        return null;
    }

    private String normalizeRemarks(
            String remarks
    ) {
        if (remarks == null) {
            return null;
        }

        return remarks
                .trim()
                .replaceAll("\\s+", " ");
    }

    private boolean isJsonRequest(
            HttpServletRequest request
    ) {
        String contentType =
                request.getContentType();

        return contentType != null
                && contentType
                        .toLowerCase(Locale.ROOT)
                        .startsWith(
                                "application/json"
                        );
    }

    private void sendInvalidLimit(
            HttpServletResponse response
    ) throws IOException {

        sendError(
                response,
                HttpServletResponse.SC_BAD_REQUEST,
                "The limit parameter must be a whole number from 1 to 100."
        );
    }

    private void invalidateSession(
            HttpServletRequest request
    ) {
        HttpSession session =
                request.getSession(false);

        if (session == null) {
            return;
        }

        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // The session was already invalidated.
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

    private static final class PayloadTooLargeException
            extends Exception {
    }
}