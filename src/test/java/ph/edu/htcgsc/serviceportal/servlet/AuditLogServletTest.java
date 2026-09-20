package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.dao.AuditLogDAO;

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

class AuditLogServletTest {

    @Test
    void unauthenticatedGetIsRejected() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new AuditLogServlet((actor, filters, cursor, limit) -> page()).doGet(request(null, null, Map.of()), response.proxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void nonAdministratorRolesAreRejected() throws Exception {
        for (int role : new int[]{1, 3, 4}) {
            RecordingResponse response = new RecordingResponse();
            new AuditLogServlet((actor, filters, cursor, limit) -> page()).doGet(request(17, role, Map.of()), response.proxy());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
        }
    }

    @Test
    void administratorUsesSessionIdentityAndDefaultBoundedLimit() throws Exception {
        int[] captured = new int[2];
        RecordingResponse response = new RecordingResponse();
        new AuditLogServlet((actor, filters, cursor, limit) -> {
            captured[0] = actor;
            captured[1] = limit;
            return page();
        }).doGet(request(17, 2, Map.of()), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertEquals(17, captured[0]);
        assertEquals(AuditLogDAO.DEFAULT_LIMIT, captured[1]);
    }

    @Test
    void validRequestedLimitIsPassedToReader() throws Exception {
        int[] captured = new int[1];
        RecordingResponse response = new RecordingResponse();
        new AuditLogServlet((actor, filters, cursor, limit) -> {
            captured[0] = limit;
            return page();
        }).doGet(request(17, 2, Map.of("limit", "25")), response.proxy());
        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertEquals(25, captured[0]);
    }

    @Test
    void malformedAndOutOfRangeLimitsAreRejected() throws Exception {
        for (String value : new String[]{"zero", "0", "101"}) {
            RecordingResponse response = new RecordingResponse();
            new AuditLogServlet((actor, filters, cursor, limit) -> page()).doGet(
                    request(17, 2, Map.of("limit", value)), response.proxy());
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status, value);
        }
    }

    @Test
    void malformedQueryParametersAreRejected() throws Exception {
        List<Map<String, String>> invalid = List.of(
                Map.of("cursor", "not-a-cursor"),
                Map.of("actorId", "0"), Map.of("requestId", "bad"), Map.of("workOrderId", "-1"),
                Map.of("outcome", "UNKNOWN"), Map.of("actionType", "lowercase"),
                Map.of("from", "2026-01-01"), Map.of("to", "not-a-time"),
                Map.of("from", "2026-02-01T00:00:00Z", "to", "2026-01-01T00:00:00Z"));
        for (Map<String, String> parameters : invalid) {
            RecordingResponse response = new RecordingResponse();
            new AuditLogServlet((actor, filters, cursor, limit) -> page()).doGet(
                    request(17, 2, parameters), response.proxy());
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status, parameters.toString());
        }
    }

    @Test
    void inactiveAdministratorIsRejected() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new AuditLogServlet((actor, filters, cursor, limit) -> {
            throw new SecurityException("inactive");
        }).doGet(request(17, 2, Map.of()), response.proxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
    }

    @Test
    void emptyPageIsSuccessful() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new AuditLogServlet((actor, filters, cursor, limit) -> new AuditLogDAO.Page(List.of(), null))
                .doGet(request(17, 2, Map.of()), response.proxy());
        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(response.body().contains("\"entries\":[]"));
        assertTrue(response.body().contains("\"nextCursor\":null"));
    }

    @Test
    void nextCursorAndSafeFieldsAreReturned() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new AuditLogServlet((actor, filters, cursor, limit) -> page())
                .doGet(request(17, 2, Map.of()), response.proxy());
        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(response.body().contains("\"nextCursor\":\"next-page\""));
        assertTrue(response.body().contains("\"auditId\":5"));
        assertFalse(response.body().contains("Details_JSON"));
        assertFalse(response.body().contains("Client_IP_Address"));
        assertFalse(response.body().contains("Client_User_Agent"));
        assertFalse(response.body().contains("Correlation_ID"));
    }

    @Test
    void internalFailureIsSafe() throws Exception {
        RecordingResponse response = new RecordingResponse();
        new AuditLogServlet((actor, filters, cursor, limit) -> {
            throw new IllegalStateException("internal SQL detail");
        }).doGet(request(17, 2, Map.of()), response.proxy());
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.status);
        assertFalse(response.body().contains("internal SQL detail"));
    }

    private static AuditLogDAO.Page page() {
        return new AuditLogDAO.Page(List.of(new AuditLogDAO.AuditEntry(5, "2026-01-01T00:00:00Z",
                "WORK_ORDER_CREATED", "SUCCESS", "WORK_ORDER", 8L, 3L, "SR-2026-000003",
                8L, "WO-2026-0001", 17L, "Administrator", "Service Administrator / Coordinator",
                "Work order created.")), "next-page");
    }

    private static HttpServletRequest request(Integer personnelId, Integer roleId, Map<String, String> parameters) {
        Map<String, Object> attributes = new HashMap<>();
        if (personnelId != null) attributes.put("personnelId", personnelId);
        if (roleId != null) attributes.put("roleId", roleId);
        HttpSession session = personnelId == null ? null : proxy(HttpSession.class, (target, method, args) -> {
            if ("getAttribute".equals(method.getName())) return attributes.get(args[0]);
            return defaultValue(method.getReturnType());
        });
        return proxy(HttpServletRequest.class, (target, method, args) -> {
            if ("getSession".equals(method.getName())) return session;
            if ("getParameter".equals(method.getName())) return parameters.get(args[0]);
            return defaultValue(method.getReturnType());
        });
    }

    private static final class RecordingResponse {
        private int status;
        private final StringWriter output = new StringWriter();

        HttpServletResponse proxy() {
            return AuditLogServletTest.proxy(HttpServletResponse.class, (target, method, args) -> {
                if ("setStatus".equals(method.getName())) { status = (Integer) args[0]; return null; }
                if ("getWriter".equals(method.getName())) return new PrintWriter(output, true);
                return defaultValue(method.getReturnType());
            });
        }

        String body() { return output.toString(); }
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
