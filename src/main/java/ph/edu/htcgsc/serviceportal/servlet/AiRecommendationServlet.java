package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.AiRecommendationDAO;
import ph.edu.htcgsc.serviceportal.service.AiRecommendationService;
import ph.edu.htcgsc.serviceportal.service.AiServiceClient;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AiRecommendationServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(AiRecommendationServlet.class.getName());
    private static final long MAXIMUM_BODY_LENGTH = 1024;
    private final Gson gson = new Gson();
    private final AiRecommendationService.Analyzer analyzer;

    public AiRecommendationServlet() {
        this(new AiRecommendationService(new AiRecommendationDAO(), new AiServiceClient())::analyze);
    }

    AiRecommendationServlet(AiRecommendationService.Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prepare(response);
        sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST is required.");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prepare(response);
        if (!requireAdministrator(request, response)) return;
        if (!CsrfUtil.isRequestTokenValid(request)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "The security token is missing or invalid. Refresh the page and try again.");
            return;
        }
        if (!isJson(request)) {
            sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be application/json.");
            return;
        }
        if (request.getContentLengthLong() > MAXIMUM_BODY_LENGTH) {
            sendError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The advisory analysis request is too large.");
            return;
        }

        long requestId;
        try {
            requestId = parseRequestId(request.getReader());
        } catch (IllegalArgumentException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
            return;
        }

        Integer personnelId = SessionUtil.getAuthenticatedPersonnelId(request);
        Integer roleId = SessionUtil.getRoleId(request);
        try {
            AiRecommendationService.Result result = analyzer.analyze(personnelId, roleId, requestId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("available", result.available());
            payload.put("message", result.message());
            payload.put("recommendation", result);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(payload));
        } catch (AiRecommendationDAO.RequestNotFoundException exception) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "The selected service request does not exist.");
        } catch (AiRecommendationDAO.IneligibleRequestException exception) {
            sendError(response, HttpServletResponse.SC_CONFLICT,
                    "Only Submitted requests can receive advisory analysis.");
        } catch (SecurityException exception) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Service Administrator access is required.");
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Advisory analysis failed.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to process advisory analysis.");
        }
    }

    private long parseRequestId(Reader reader) {
        try {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new IllegalArgumentException("A JSON object is required.");
            JsonObject object = root.getAsJsonObject();
            if (object.size() != 1 || !object.has("requestId")) {
                throw new IllegalArgumentException("Only a requestId is accepted.");
            }
            JsonElement value = object.get("requestId");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("A positive requestId is required.");
            }
            long requestId = value.getAsLong();
            if (requestId <= 0 || new BigDecimal(value.getAsString())
                    .compareTo(BigDecimal.valueOf(requestId)) != 0) {
                throw new IllegalArgumentException("A positive requestId is required.");
            }
            return requestId;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Malformed JSON request.");
        }
    }

    private boolean requireAdministrator(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!SessionUtil.isAuthenticated(request)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication is required.");
            return false;
        }
        if (!SessionUtil.hasRole(request, SessionUtil.SERVICE_ADMINISTRATOR_ROLE_ID)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "Service Administrator access is required.");
            return false;
        }
        return true;
    }

    private boolean isJson(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase().startsWith("application/json");
    }

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().write(gson.toJson(Map.of("success", false, "message", message)));
    }
}
