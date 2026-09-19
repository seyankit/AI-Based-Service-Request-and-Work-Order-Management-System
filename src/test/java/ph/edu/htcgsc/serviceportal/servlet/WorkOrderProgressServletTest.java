package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.dao.WorkOrderProgressDAO;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderProgressServletTest {

    @Test
    void unauthenticatedGetAndPutAreRejected() throws Exception {
        RecordingResponse get = new RecordingResponse();
        new WorkOrderProgressServlet().doGet(request(null, null, null, null), get.proxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, get.status);

        RecordingResponse put = new RecordingResponse();
        new WorkOrderProgressServlet().doPut(request(null, null, null,
                "{\"action\":\"acknowledge\",\"workOrderId\":5}"), put.proxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, put.status);
    }

    @Test
    void onlyActiveTechnicianRoleReachesReaderAndMutator() throws Exception {
        for (int role : new int[] { 1, 2, 3 }) {
            RecordingResponse response = new RecordingResponse();
            new WorkOrderProgressServlet((actor, currentRole, id) -> List.of(),
                    (actor, currentRole, input) -> Map.of()).doGet(
                    request(17, role, null, null), response.proxy());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
        }

        RecordingResponse get = new RecordingResponse();
        new WorkOrderProgressServlet((actor, role, id) -> List.of(Map.of("workOrderId", 5)),
                (actor, role, input) -> Map.of()).doGet(
                request(17, 4, null, null), get.proxy());
        assertEquals(HttpServletResponse.SC_OK, get.status);
        assertTrue(get.body().contains("workOrders"));

        RecordingResponse put = new RecordingResponse();
        new WorkOrderProgressServlet((actor, role, id) -> List.of(),
                (actor, role, input) -> Map.of("workOrderId", input.workOrderId(), "status", "Acknowledged"))
                .doPut(request(17, 4, "csrf-token",
                        "{\"action\":\"acknowledge\",\"workOrderId\":5,"
                                + "\"progressNotes\":\"Acknowledged assigned work.\"}"), put.proxy());
        assertEquals(HttpServletResponse.SC_OK, put.status);
    }

    @Test
    void inactiveTechnicianAndOwnershipFailuresMapToForbidden() throws Exception {
        WorkOrderProgressServlet inactive = new WorkOrderProgressServlet(
                (actor, role, id) -> { throw new SecurityException("inactive"); },
                (actor, role, input) -> Map.of());
        RecordingResponse response = new RecordingResponse();
        inactive.doGet(request(17, 4, null, null), response.proxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);

        WorkOrderProgressServlet owner = new WorkOrderProgressServlet(
                (actor, role, id) -> List.of(),
                (actor, role, input) -> { throw new WorkOrderProgressDAO.ProgressException(
                        WorkOrderProgressDAO.Failure.NOT_CURRENT_ASSIGNEE, "not yours"); });
        response = new RecordingResponse();
        owner.doPut(request(17, 4, "csrf-token", validBody("start")), response.proxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
    }

    @Test
    void queueDetailAndInvalidWorkOrderIdsAreHandled() throws Exception {
        WorkOrderProgressServlet servlet = new WorkOrderProgressServlet(
                (actor, role, id) -> id == null ? List.of(Map.of("workOrderId", 5)) : List.of(),
                (actor, role, input) -> Map.of());
        RecordingResponse detail = new RecordingResponse();
        servlet.doGet(request(17, 4, null, null, "application/json", "7"), detail.proxy());
        assertEquals(HttpServletResponse.SC_NOT_FOUND, detail.status);

        RecordingResponse invalid = new RecordingResponse();
        servlet.doGet(request(17, 4, null, null, "application/json", "zero"), invalid.proxy());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, invalid.status);
    }

    @Test
    void mutationValidationRejectsUnknownActionIdentifiersNotesPercentagesAndSummary() throws Exception {
        WorkOrderProgressServlet servlet = new WorkOrderProgressServlet(
                (actor, role, id) -> List.of(), (actor, role, input) -> Map.of());
        String[] bodies = {
                "{}",
                "{\"action\":\"unknown\",\"workOrderId\":5,\"progressNotes\":\"valid notes\"}",
                "{\"action\":\"verify\",\"workOrderId\":5,\"progressNotes\":\"valid notes\"}",
                "{\"action\":\"progress\",\"workOrderId\":0,\"progressNotes\":\"valid notes\",\"progressPercentage\":40}",
                "{\"action\":\"progress\",\"workOrderId\":5,\"progressNotes\":\"x\",\"progressPercentage\":40}",
                "{\"action\":\"progress\",\"workOrderId\":5,\"progressNotes\":\"valid notes\",\"progressPercentage\":0}",
                "{\"action\":\"progress\",\"workOrderId\":5,\"progressNotes\":\"valid notes\",\"progressPercentage\":100}",
                "{\"action\":\"complete\",\"workOrderId\":5,\"progressNotes\":\"valid notes\",\"completionSummary\":\"short\"}"
        };
        for (String body : bodies) {
            RecordingResponse response = new RecordingResponse();
            servlet.doPut(request(17, 4, "csrf-token", body), response.proxy());
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status, body);
        }
    }

    @Test
    void mutationUsesSessionIdentityAndExactAllowedInput() throws Exception {
        WorkOrderProgressDAO.MutationInput[] captured = { null };
        int[] identity = { 0, 0 };
        WorkOrderProgressServlet servlet = new WorkOrderProgressServlet(
                (actor, role, id) -> List.of(),
                (actor, role, input) -> {
                    identity[0] = actor;
                    identity[1] = role;
                    captured[0] = input;
                    return Map.of("workOrderId", input.workOrderId(), "status", "In Progress");
                });
        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(17, 4, "csrf-token",
                "{\"action\":\"progress\",\"workOrderId\":5,\"progressPercentage\":40,"
                        + "\"progressNotes\":\" Network inspection continues. \","
                        + "\"technicianId\":999,\"newStatus\":\"Completed\"}"), response.proxy());
        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertEquals(17, identity[0]);
        assertEquals(4, identity[1]);
        assertEquals("progress", captured[0].action());
        assertEquals(5L, captured[0].workOrderId());
        assertEquals(40, captured[0].progressPercentage());
        assertEquals("Network inspection continues.", captured[0].progressNotes());
        assertEquals(null, captured[0].completionSummary());
    }

    @Test
    void lifecycleFailuresAreMappedToConflictOrNotFound() throws Exception {
        for (WorkOrderProgressDAO.Failure failure : new WorkOrderProgressDAO.Failure[] {
                WorkOrderProgressDAO.Failure.WORK_ORDER_NOT_FOUND,
                WorkOrderProgressDAO.Failure.ASSIGNMENT_NOT_FOUND
        }) {
            RecordingResponse response = new RecordingResponse();
            WorkOrderProgressServlet servlet = new WorkOrderProgressServlet(
                    (actor, role, id) -> List.of(),
                    (actor, role, input) -> { throw new WorkOrderProgressDAO.ProgressException(
                            failure, "missing"); });
            servlet.doPut(request(17, 4, "csrf-token", validBody("acknowledge")), response.proxy());
            assertEquals(HttpServletResponse.SC_NOT_FOUND, response.status);
        }
        for (WorkOrderProgressDAO.Failure failure : new WorkOrderProgressDAO.Failure[] {
                WorkOrderProgressDAO.Failure.STALE_STATE
        }) {
            RecordingResponse response = new RecordingResponse();
            WorkOrderProgressServlet servlet = new WorkOrderProgressServlet(
                    (actor, role, id) -> List.of(),
                    (actor, role, input) -> { throw new WorkOrderProgressDAO.ProgressException(
                            failure, "stale"); });
            servlet.doPut(request(17, 4, "csrf-token", validBody("start")), response.proxy());
            assertEquals(HttpServletResponse.SC_CONFLICT, response.status);
        }
    }

    @Test
    void malformedJsonCsrfAndContentTypeAreRejected() throws Exception {
        WorkOrderProgressServlet servlet = new WorkOrderProgressServlet(
                (actor, role, id) -> List.of(), (actor, role, input) -> Map.of());
        for (String body : new String[] { "{invalid", "{}" }) {
            RecordingResponse response = new RecordingResponse();
            servlet.doPut(request(17, 4, "csrf-token", body), response.proxy());
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        }
        RecordingResponse csrf = new RecordingResponse();
        servlet.doPut(request(17, 4, null, validBody("start")), csrf.proxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, csrf.status);
        RecordingResponse contentType = new RecordingResponse();
        servlet.doPut(request(17, 4, "csrf-token", validBody("start"), "text/plain"), contentType.proxy());
        assertEquals(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, contentType.status);
    }

    private static String validBody(String action) {
        return "{\"action\":\"" + action + "\",\"workOrderId\":5,"
                + "\"progressNotes\":\"Valid technician notes.\"}";
    }

    private static HttpServletRequest request(Integer personnelId, Integer roleId,
                                               String csrfToken, String body) {
        return request(personnelId, roleId, csrfToken, body, "application/json", null);
    }

    private static HttpServletRequest request(Integer personnelId, Integer roleId,
                                               String csrfToken, String body, String contentType) {
        return request(personnelId, roleId, csrfToken, body, contentType, null);
    }

    private static HttpServletRequest request(Integer personnelId, Integer roleId,
                                               String csrfToken, String body, String ignored, String workOrderId) {
        Map<String, Object> attributes = new HashMap<>();
        if (personnelId != null) attributes.put("personnelId", personnelId);
        if (roleId != null) attributes.put("roleId", roleId);
        if (csrfToken != null) attributes.put("csrfToken", csrfToken);
        HttpSession session = personnelId == null ? null : proxy(HttpSession.class, (proxy, method, args) -> {
            if ("getAttribute".equals(method.getName())) return attributes.get(args[0]);
            return defaultValue(method.getReturnType());
        });
        return proxy(HttpServletRequest.class, (proxy, method, args) -> {
            if ("getSession".equals(method.getName())) return session;
            if ("getHeader".equals(method.getName())) {
                return "X-CSRF-Token".equals(args[0]) ? csrfToken : null;
            }
            if ("getContentType".equals(method.getName())) return ignored == null ? "application/json" : ignored;
            if ("getContentLengthLong".equals(method.getName())) return body == null ? 0L : (long) body.length();
            if ("getReader".equals(method.getName())) return new BufferedReader(new StringReader(body == null ? "" : body));
            if ("getParameter".equals(method.getName())) return "workOrderId".equals(args[0]) ? workOrderId : null;
            return defaultValue(method.getReturnType());
        });
    }

    private static final class RecordingResponse {
        private int status;
        private final StringWriter output = new StringWriter();

        HttpServletResponse proxy() {
            return WorkOrderProgressServletTest.proxy(HttpServletResponse.class, (target, method, args) -> {
                if ("setStatus".equals(method.getName())) {
                    status = (Integer) args[0];
                    return null;
                }
                if ("getWriter".equals(method.getName())) return new PrintWriter(output, true);
                return defaultValue(method.getReturnType());
            });
        }

        String body() { return output.toString(); }
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
