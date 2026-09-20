package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.dao.WorkOrderHistoryDAO;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderHistoryServletTest {

    @Test
    void unauthenticatedGetIsRejected() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new WorkOrderHistoryServlet((actor, role, id) -> result(id)).doGet(
                request(null, null, "5"), response.proxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void requesterAndDepartmentHeadAreRejected() throws Exception {
        for (int role : new int[] { 1, 3 }) {
            RecordingResponse response = new RecordingResponse();
            new WorkOrderHistoryServlet((actor, currentRole, id) -> result(id)).doGet(
                    request(17, role, "5"), response.proxy());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
        }
    }

    @Test
    void missingMalformedZeroAndNegativeWorkOrderIdsAreRejected() throws Exception {
        for (String value : new String[] { null, "invalid", "0", "-5" }) {
            RecordingResponse response = new RecordingResponse();
            new WorkOrderHistoryServlet((actor, role, id) -> result(id)).doGet(
                    request(17, 2, value), response.proxy());
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status, String.valueOf(value));
        }
    }

    @Test
    void administratorUsesSessionIdentityAndReceivesBothTimelines() throws Exception {
        int[] captured = new int[2];
        RecordingResponse response = new RecordingResponse();
        new WorkOrderHistoryServlet((actor, role, id) -> {
            captured[0] = actor;
            captured[1] = role;
            return result(id);
        }).doGet(request(17, 2, "5"), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertEquals(17, captured[0]);
        assertEquals(2, captured[1]);
        assertTrue(response.body().contains("workOrderEvents"));
        assertTrue(response.body().contains("requestEvents"));
    }

    @Test
    void technicianCurrentAssignmentHistorySucceedsWithoutRequestEvents() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new WorkOrderHistoryServlet((actor, role, id) -> result(id)).doGet(
                request(15, 4, "5"), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(response.body().contains("workOrderEvents"));
        assertFalse(response.body().contains("requestEvents"));
    }

    @Test
    void technicianForeignAndMissingWorkOrdersAreNonEnumeratingNotFound() throws Exception {
        WorkOrderHistoryServlet servlet = new WorkOrderHistoryServlet(
                (actor, role, id) -> new WorkOrderHistoryDAO.LookupResult(false, id, null, List.of(), List.of()));
        RecordingResponse foreign = new RecordingResponse();
        servlet.doGet(request(15, 4, "7"), foreign.proxy());
        RecordingResponse missing = new RecordingResponse();
        servlet.doGet(request(15, 4, "8"), missing.proxy());

        assertEquals(HttpServletResponse.SC_NOT_FOUND, foreign.status);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, missing.status);
        assertEquals(foreign.body(), missing.body());
    }

    @Test
    void emptyTimelineIsSuccessful() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new WorkOrderHistoryServlet((actor, role, id) ->
                new WorkOrderHistoryDAO.LookupResult(true, id, "WO-2026-0001", List.of(), List.of()))
                .doGet(request(17, 2, "5"), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(response.body().contains("\"workOrderEvents\":[]"));
        assertTrue(response.body().contains("\"requestEvents\":[]"));
    }

    @Test
    void inactiveOrUnauthorizedDatabaseActorIsRejected() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new WorkOrderHistoryServlet((actor, role, id) -> {
            throw new SecurityException("inactive actor");
        }).doGet(request(15, 4, "5"), response.proxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
    }

    @Test
    void internalFailureReturnsSafeServerError() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new WorkOrderHistoryServlet((actor, role, id) -> {
            throw new IllegalStateException("internal SQL detail");
        }).doGet(request(17, 2, "5"), response.proxy());

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.status);
        assertFalse(response.body().contains("internal SQL detail"));
    }

    private static WorkOrderHistoryDAO.LookupResult result(long id) {
        return new WorkOrderHistoryDAO.LookupResult(true, id, "WO-2026-0001",
                List.of(Map.of("source", "WORK_ORDER_HISTORY", "eventType", "Created",
                        "title", "Created", "previousStatus", "", "newStatus", "Created",
                        "remarks", "Work order created.", "actorId", 17, "occurredAt", "2026-01-01T00:00:00Z")),
                List.of(Map.of("source", "REQUEST_STATUS_HISTORY", "eventType", "Request Status Changed",
                        "title", "Request status changed", "previousStatus", "Submitted",
                        "newStatus", "Approved", "remarks", "Approved.", "actorId", 17,
                        "occurredAt", "2026-01-01T00:00:00Z")));
    }

    private static HttpServletRequest request(Integer personnelId, Integer roleId, String workOrderId) {
        Map<String, Object> attributes = new HashMap<>();
        if (personnelId != null) attributes.put("personnelId", personnelId);
        if (roleId != null) attributes.put("roleId", roleId);
        HttpSession session = personnelId == null ? null : proxy(HttpSession.class, (proxy, method, args) -> {
            if ("getAttribute".equals(method.getName())) return attributes.get(args[0]);
            return defaultValue(method.getReturnType());
        });
        return proxy(HttpServletRequest.class, (proxy, method, args) -> {
            if ("getSession".equals(method.getName())) return session;
            if ("getParameter".equals(method.getName())) {
                return "workOrderId".equals(args[0]) ? workOrderId : null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static final class RecordingResponse {
        private int status;
        private final StringWriter output = new StringWriter();

        HttpServletResponse proxy() {
            return WorkOrderHistoryServletTest.proxy(HttpServletResponse.class, (target, method, args) -> {
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

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }
}
