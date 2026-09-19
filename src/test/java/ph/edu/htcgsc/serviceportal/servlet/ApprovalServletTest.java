package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalServletTest {

    @Test
    void getUnauthenticatedReturnsUnauthorized() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ApprovalServlet().doGet(request(null, null, null, null), response.proxy());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void putUnauthenticatedReturnsUnauthorized() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ApprovalServlet().doPut(request(null, null, null, null), response.proxy());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void requesterAndTechnicianAreRejectedForGetAndPut() throws Exception {
        for (int role : new int[] { 1, 4 }) {
            RecordingResponse getResponse = new RecordingResponse();
            new ApprovalServlet().doGet(
                    request(17, role, null, null),
                    getResponse.proxy());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, getResponse.status);

            RecordingResponse putResponse = new RecordingResponse();
            new ApprovalServlet().doPut(
                    request(17, role, null, "{}"),
                    putResponse.proxy());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, putResponse.status);
        }
    }

    @Test
    void roleTwoCannotMakeApprovalDecision() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ApprovalServlet().doPut(
                request(17, 2, "csrf-token", "{}"),
                response.proxy());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
    }

    @Test
    void invalidApprovalIdReturnsBadRequest() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ApprovalServlet().doPut(
                request(17, 3, "csrf-token", "{\"approvalId\":0}"),
                response.proxy());

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ApprovalServlet().doPut(
                request(17, 3, "csrf-token", "{invalid"),
                response.proxy());

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ApprovalServlet().doPut(
                request(17, 3, "csrf-token", "{}", "text/plain"),
                response.proxy());

        assertEquals(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, response.status);
    }

    @Test
    void invalidDecisionReturnsBadRequest() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ApprovalServlet().doPut(
                request(17, 3, "csrf-token", "{\"approvalId\":1,\"decision\":\"Pending\",\"remarks\":\"Enough\"}"),
                response.proxy());

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    }

    @Test
    void missingAndOutOfRangeRemarksReturnBadRequest() throws Exception {
        for (String remarks : new String[] { null, "ab", "x".repeat(1001) }) {
            RecordingResponse response = new RecordingResponse();
            String json = remarks == null
                    ? "{\"approvalId\":1,\"decision\":\"Approved\"}"
                    : "{\"approvalId\":1,\"decision\":\"Approved\",\"remarks\":\""
                            + remarks + "\"}";

            new ApprovalServlet().doPut(
                    request(17, 3, "csrf-token", json),
                    response.proxy());

            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        }
    }

    @Test
    void roleThreeReachesApprovalDecisionLayerForValidInput() throws Exception {
        RecordingResponse response = new RecordingResponse();
        boolean[] decisionCalled = { false };

        ApprovalServlet servlet = new ApprovalServlet(
                (actor, role, status) -> java.util.List.of(),
                (actor, role, approvalId, decision, remarks) -> {
                    decisionCalled[0] = true;

                    assertEquals(17, actor);
                    assertEquals(3, role);
                    assertEquals(1L, approvalId);
                    assertEquals("Approved", decision);
                    assertEquals("Reviewed", remarks);
                });

        servlet.doPut(
                request(
                        17,
                        3,
                        "csrf-token",
                        "{\"approvalId\":1,\"decision\":\"Approved\",\"remarks\":\"Reviewed\"}"),
                response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(decisionCalled[0]);
        assertTrue(
                response.body().contains(
                        "Approval decision recorded successfully."));
    }

    private static HttpServletRequest request(
            Integer personnelId,
            Integer roleId,
            String csrfToken,
            String body) {
        return request(personnelId, roleId, csrfToken, body, "application/json");
    }

    private static HttpServletRequest request(
            Integer personnelId,
            Integer roleId,
            String csrfToken,
            String body,
            String contentType) {
        Map<String, Object> attributes = new HashMap<>();
        if (personnelId != null) {
            attributes.put("personnelId", personnelId);
        }
        if (roleId != null) {
            attributes.put("roleId", roleId);
        }
        if (csrfToken != null) {
            attributes.put("csrfToken", csrfToken);
        }

        HttpSession session = personnelId == null
                ? null
                : proxy(HttpSession.class, (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) {
                        return attributes.get(args[0]);
                    }
                    return defaultValue(method.getReturnType());
                });

        return proxy(HttpServletRequest.class, (proxy, method, args) -> {
            if ("getSession".equals(method.getName())) {
                return session;
            }
            if ("getHeader".equals(method.getName())) {
                return "X-CSRF-Token".equals(args[0]) ? csrfToken : null;
            }
            if ("getContentType".equals(method.getName())) {
                return contentType;
            }
            if ("getContentLengthLong".equals(method.getName())) {
                return body == null ? 0L : (long) body.length();
            }
            if ("getReader".equals(method.getName())) {
                return new BufferedReader(new StringReader(body == null ? "" : body));
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static final class RecordingResponse {
        private int status;
        private final StringWriter output = new StringWriter();

        HttpServletResponse proxy() {
            return ApprovalServletTest.proxy(
                    HttpServletResponse.class,
                    (target, method, args) -> {
                        if ("setStatus".equals(method.getName())) {
                            status = (Integer) args[0];
                            return null;
                        }
                        if ("getWriter".equals(method.getName())) {
                            return new PrintWriter(output, true);
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        String body() {
            return output.toString();
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] { type },
                handler);
    }
}
