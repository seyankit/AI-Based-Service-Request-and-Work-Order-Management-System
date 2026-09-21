package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.ServiceInsightsDAO;
import ph.edu.htcgsc.serviceportal.util.ApiJson;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Role-2, read-only operational aggregates for the Administrator Overview. */
public final class ServiceInsightsServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(ServiceInsightsServlet.class.getName());
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    @FunctionalInterface
    interface InsightsReader {
        ServiceInsightsDAO.Insights load(ServiceInsightsDAO.Period period) throws Exception;
    }

    private final InsightsReader insightsReader;

    public ServiceInsightsServlet() {
        this(new ServiceInsightsDAO()::load);
    }

    ServiceInsightsServlet(InsightsReader insightsReader) {
        this.insightsReader = insightsReader;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prepare(response);
        if (!SessionUtil.isAuthenticated(request)) {
            ApiJson.error(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication is required.");
            return;
        }
        if (!SessionUtil.hasRole(request, SessionUtil.SERVICE_ADMINISTRATOR_ROLE_ID)) {
            ApiJson.error(response, HttpServletResponse.SC_FORBIDDEN,
                    "Service Administrator access is required.");
            return;
        }

        try {
            ServiceInsightsDAO.Period period = parsePeriod(request);
            ServiceInsightsDAO.Insights insights = insightsReader.load(period);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("period", period.value());
            payload.put("summary", insights.summary());
            payload.put("requestsByCategory", insights.requestsByCategory());
            payload.put("requestsByPriority", insights.requestsByPriority());
            payload.put("workOrdersByStatus", insights.workOrdersByStatus());
            payload.put("requestTrend", insights.requestTrend());
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(GSON.toJson(payload));
        } catch (IllegalArgumentException exception) {
            ApiJson.error(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Service-insights retrieval failed.", exception);
            ApiJson.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load service insights.");
        }
    }

    private ServiceInsightsDAO.Period parsePeriod(HttpServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();
        if (parameters == null || parameters.isEmpty()) {
            return ServiceInsightsDAO.Period.LAST_30_DAYS;
        }
        if (parameters.size() != 1 || !parameters.containsKey("period")) {
            throw new IllegalArgumentException("Only the period parameter is supported.");
        }
        String[] values = parameters.get("period");
        if (values == null || values.length != 1 || values[0] == null || values[0].isBlank()) {
            throw new IllegalArgumentException("The period parameter must appear exactly once.");
        }
        return ServiceInsightsDAO.Period.fromValue(values[0]);
    }

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
