package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.dao.ServiceInsightsDAO;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceInsightsServletTest {

    @Test
    void unauthenticatedGetIsRejected() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new ServiceInsightsServlet(period -> insights()).doGet(request(null, null, Map.of()), response.proxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void nonAdministratorRolesAreRejected() throws Exception {
        for (int role : new int[]{1, 3, 4}) {
            RecordingResponse response = new RecordingResponse();
            new ServiceInsightsServlet(period -> insights()).doGet(request(17, role, Map.of()), response.proxy());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status, "role=" + role);
        }
    }

    @Test
    void defaultPeriodIsThirtyDays() throws Exception {
        ServiceInsightsDAO.Period[] captured = new ServiceInsightsDAO.Period[1];
        RecordingResponse response = new RecordingResponse();
        new ServiceInsightsServlet(period -> {
            captured[0] = period;
            return insights();
        }).doGet(request(17, 2, Map.of()), response.proxy());
        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertEquals(ServiceInsightsDAO.Period.LAST_30_DAYS, captured[0]);
        assertTrue(response.body().contains("\"period\":\"30d\""));
    }

    @Test
    void eachSupportedPeriodIsAccepted() throws Exception {
        Map<String, ServiceInsightsDAO.Period> periods = Map.of(
                "7d", ServiceInsightsDAO.Period.LAST_7_DAYS,
                "30d", ServiceInsightsDAO.Period.LAST_30_DAYS,
                "month", ServiceInsightsDAO.Period.CURRENT_MONTH,
                "all", ServiceInsightsDAO.Period.ALL_TIME
        );
        for (Map.Entry<String, ServiceInsightsDAO.Period> entry : periods.entrySet()) {
            ServiceInsightsDAO.Period[] captured = new ServiceInsightsDAO.Period[1];
            RecordingResponse response = new RecordingResponse();
            new ServiceInsightsServlet(period -> {
                captured[0] = period;
                return insights();
            }).doGet(request(17, 2, Map.of("period", new String[]{entry.getKey()})), response.proxy());
            assertEquals(HttpServletResponse.SC_OK, response.status, entry.getKey());
            assertEquals(entry.getValue(), captured[0], entry.getKey());
        }
    }

    @Test
    void invalidPeriodIsRejected() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new ServiceInsightsServlet(period -> insights()).doGet(
                request(17, 2, Map.of("period", new String[]{"tomorrow"})), response.proxy());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    }

    @Test
    void repeatedOrBlankPeriodIsRejected() throws Exception {
        for (String[] values : List.of(new String[]{"7d", "30d"}, new String[]{""})) {
            RecordingResponse response = new RecordingResponse();
            new ServiceInsightsServlet(period -> insights()).doGet(
                    request(17, 2, Map.of("period", values)), response.proxy());
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        }
    }

    @Test
    void unknownParameterIsRejected() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new ServiceInsightsServlet(period -> insights()).doGet(
                request(17, 2, Map.of("limit", new String[]{"10"})), response.proxy());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    }

    @Test
    void emptyAggregatesAreSuccessful() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new ServiceInsightsServlet(period -> emptyInsights()).doGet(request(17, 2, Map.of()), response.proxy());
        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(response.body().contains("\"requestsByCategory\":[]"));
        assertTrue(response.body().contains("\"averageCompletionHours\":null"));
    }

    @Test
    void aggregateOnlyFieldsAreReturned() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new ServiceInsightsServlet(period -> insights()).doGet(request(17, 2, Map.of()), response.proxy());
        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(response.body().contains("\"requestsByCategory\""));
        assertTrue(response.body().contains("\"workOrdersByStatus\""));
        assertTrue(response.body().contains("\"requestTrend\""));
        assertFalse(response.body().contains("requesterEmail"));
        assertFalse(response.body().contains("requestDescription"));
        assertFalse(response.body().contains("contactNumber"));
    }

    @Test
    void internalFailureIsSafe() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new ServiceInsightsServlet(period -> {
            throw new IllegalStateException("internal SQL detail");
        }).doGet(request(17, 2, Map.of()), response.proxy());
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.status);
        assertFalse(response.body().contains("internal SQL detail"));
    }

    private static ServiceInsightsDAO.Insights insights() {
        return new ServiceInsightsDAO.Insights(
                new ServiceInsightsDAO.Summary(8, 2, 1, 3, 1, 5, 2, new BigDecimal("4.25")),
                List.of(new ServiceInsightsDAO.CategoryCount(1L, "IT & Computer", 4)),
                List.of(new ServiceInsightsDAO.PriorityCount("High", 2)),
                List.of(new ServiceInsightsDAO.WorkOrderStatusCount("In Progress", 1)),
                List.of(new ServiceInsightsDAO.TrendPoint("2026-09-21", 3))
        );
    }

    private static ServiceInsightsDAO.Insights emptyInsights() {
        return new ServiceInsightsDAO.Insights(
                new ServiceInsightsDAO.Summary(0, 0, 0, 0, 0, 0, 0, null),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static HttpServletRequest request(Integer personnelId, Integer roleId,
                                              Map<String, String[]> parameters) {
        Map<String, Object> attributes = new HashMap<>();
        if (personnelId != null) attributes.put("personnelId", personnelId);
        if (roleId != null) attributes.put("roleId", roleId);
        HttpSession session = personnelId == null ? null : proxy(HttpSession.class, (target, method, args) -> {
            if ("getAttribute".equals(method.getName())) return attributes.get(args[0]);
            return defaultValue(method.getReturnType());
        });
        return proxy(HttpServletRequest.class, (target, method, args) -> {
            if ("getSession".equals(method.getName())) return session;
            if ("getParameterMap".equals(method.getName())) return parameters;
            if ("getParameter".equals(method.getName())) {
                String[] values = parameters.get(args[0]);
                return values == null || values.length == 0 ? null : values[0];
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static final class RecordingResponse {
        private int status;
        private final StringWriter output = new StringWriter();

        HttpServletResponse proxy() {
            return ServiceInsightsServletTest.proxy(HttpServletResponse.class, (target, method, args) -> {
                if ("setStatus".equals(method.getName())) {
                    status = (Integer) args[0];
                    return null;
                }
                if ("getWriter".equals(method.getName())) return new PrintWriter(output, true);
                return defaultValue(method.getReturnType());
            });
        }

        String body() {
            return output.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }
}
