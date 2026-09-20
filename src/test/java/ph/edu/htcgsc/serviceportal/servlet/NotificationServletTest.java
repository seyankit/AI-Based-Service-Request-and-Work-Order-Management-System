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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServletTest {

    @Test
    void unauthenticatedGetIsRejected() throws Exception {
        NotificationServlet servlet = new NotificationServlet(null, null, null, null, null);
        RecordingResponse response = new RecordingResponse();
        servlet.doGet(request(null, null, null, null), response.proxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void authenticatedGetUsesSessionPersonnelIdAndReturnsData() throws Exception {
        NotificationServlet.UnreadCountSupplier countSupplier = personnelId -> {
            assertEquals(17, personnelId);
            return 3;
        };
        NotificationServlet.NotificationLister lister = personnelId -> {
            assertEquals(17, personnelId);
            return List.of(Map.of("notificationId", 100L));
        };
        NotificationServlet servlet = new NotificationServlet(countSupplier, lister, null, null, null);

        RecordingResponse response = new RecordingResponse();
        servlet.doGet(request(17, 1, null, null), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        String body = response.body();
        assertTrue(body.contains("\"unreadCount\":3"));
        assertTrue(body.contains("\"notificationId\":100"));
    }

    @Test
    void getIgnoresClientProvidedRecipientId() throws Exception {
        NotificationServlet.UnreadCountSupplier countSupplier = personnelId -> {
            assertEquals(17, personnelId);
            return 0;
        };
        NotificationServlet.NotificationLister lister = personnelId -> {
            assertEquals(17, personnelId);
            return List.of();
        };
        NotificationServlet servlet = new NotificationServlet(countSupplier, lister, null, null, null);

        RecordingResponse response = new RecordingResponse();
        servlet.doGet(requestWithParams(17, 1, null, null, Map.of("recipientId", "999")), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
    }

    @Test
    void unauthenticatedMarkReadIsRejected() throws Exception {
        NotificationServlet servlet = new NotificationServlet(null, null, null, null, null);
        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(null, null, null, "{\"action\":\"markRead\",\"notificationId\":1}"), response.proxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void markReadRequiresCsrf() throws Exception {
        NotificationServlet servlet = new NotificationServlet(null, null, null, null, null);
        RecordingResponse response = new RecordingResponse();
        // Passing different csrf in header vs session causes CsrfUtil to fail
        servlet.doPut(requestWithMismatchCsrf(17, 1, "session-token", "header-token", "{\"action\":\"markRead\",\"notificationId\":1}"), response.proxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
    }

    @Test
    void invalidNotificationIdRejected() throws Exception {
        NotificationServlet servlet = new NotificationServlet(null, null, null, null, null);
        String[] bodies = {
                "{\"action\":\"markRead\"}",
                "{\"action\":\"markRead\",\"notificationId\":0}",
                "{\"action\":\"markRead\",\"notificationId\":-1}",
                "{\"action\":\"markRead\",\"notificationId\":\"abc\"}"
        };

        for (String body : bodies) {
            RecordingResponse response = new RecordingResponse();
            servlet.doPut(request(17, 1, "valid-token", body), response.proxy());
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        }
    }

    @Test
    void ownUnreadNotificationCanBeMarkedRead() throws Exception {
        NotificationServlet.NotificationMutator mutator = (notificationId, personnelId) -> {
            assertEquals(100L, notificationId);
            assertEquals(17, personnelId);
            return 1;
        };
        NotificationServlet.UnreadCountSupplier countSupplier = personnelId -> 2;
        NotificationServlet servlet = new NotificationServlet(countSupplier, null, mutator, null, null);

        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(17, 1, "valid-token", "{\"action\":\"markRead\",\"notificationId\":100}"), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(response.body().contains("\"unreadCount\":2"));
    }

    @Test
    void foreignNotificationCannotBeMarkedRead() throws Exception {
        NotificationServlet.NotificationMutator mutator = (notificationId, personnelId) -> 0;
        NotificationServlet.NotificationReader reader = (notificationId, personnelId) -> null;
        NotificationServlet servlet = new NotificationServlet(null, null, mutator, null, reader);

        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(17, 1, "valid-token", "{\"action\":\"markRead\",\"notificationId\":100}"), response.proxy());

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.status);
    }

    @Test
    void nonexistentNotificationBehavesSameAsForeign() throws Exception {
        NotificationServlet.NotificationMutator mutator = (notificationId, personnelId) -> 0;
        NotificationServlet.NotificationReader reader = (notificationId, personnelId) -> null;
        NotificationServlet servlet = new NotificationServlet(null, null, mutator, null, reader);

        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(17, 1, "valid-token", "{\"action\":\"markRead\",\"notificationId\":999}"), response.proxy());

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.status);
    }

    @Test
    void ownAlreadyReadNotificationSucceedsIdempotently() throws Exception {
        NotificationServlet.NotificationMutator mutator = (notificationId, personnelId) -> 0;
        NotificationServlet.NotificationReader reader = (notificationId, personnelId) ->
                Map.of("notificationId", 100L, "isRead", true);
        NotificationServlet.UnreadCountSupplier countSupplier = personnelId -> 2;
        NotificationServlet servlet = new NotificationServlet(countSupplier, null, mutator, null, reader);

        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(17, 1, "valid-token", "{\"action\":\"markRead\",\"notificationId\":100}"), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
    }

    @Test
    void markAllReadRequiresAuthentication() throws Exception {
        NotificationServlet servlet = new NotificationServlet(null, null, null, null, null);
        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(null, null, null, "{\"action\":\"markAllRead\"}"), response.proxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void markAllReadRequiresCsrf() throws Exception {
        NotificationServlet servlet = new NotificationServlet(null, null, null, null, null);
        RecordingResponse response = new RecordingResponse();
        servlet.doPut(requestWithMismatchCsrf(17, 1, "session-token", "header-token", "{\"action\":\"markAllRead\"}"), response.proxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
    }

    @Test
    void markAllReadIsRecipientScoped() throws Exception {
        NotificationServlet.NotificationAllMutator allMutator = personnelId -> {
            assertEquals(17, personnelId);
            return 5;
        };
        NotificationServlet.UnreadCountSupplier countSupplier = personnelId -> 0;
        NotificationServlet servlet = new NotificationServlet(countSupplier, null, null, allMutator, null);

        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(17, 1, "valid-token", "{\"action\":\"markAllRead\"}"), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(response.body().contains("\"updatedCount\":5"));
        assertTrue(response.body().contains("\"unreadCount\":0"));
    }

    @Test
    void unknownActionRejected() throws Exception {
        NotificationServlet servlet = new NotificationServlet(null, null, null, null, null);
        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(17, 1, "valid-token", "{\"action\":\"unknown\"}"), response.proxy());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    }

    @Test
    void malformedJsonRejectedSafely() throws Exception {
        NotificationServlet servlet = new NotificationServlet(null, null, null, null, null);
        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(17, 1, "valid-token", "{malformed"), response.proxy());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    }

    @Test
    void getSqlFailureReturnsSafe500() throws Exception {
        NotificationServlet.UnreadCountSupplier countSupplier = personnelId -> {
            throw new java.sql.SQLException("DB error");
        };
        NotificationServlet servlet = new NotificationServlet(countSupplier, null, null, null, null);

        RecordingResponse response = new RecordingResponse();
        servlet.doGet(request(17, 1, null, null), response.proxy());

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.status);
    }

    @Test
    void markReadSqlFailureReturnsSafe500() throws Exception {
        NotificationServlet.NotificationMutator mutator = (notificationId, personnelId) -> {
            throw new java.sql.SQLException("DB error");
        };
        NotificationServlet servlet = new NotificationServlet(null, null, mutator, null, null);

        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(17, 1, "valid-token", "{\"action\":\"markRead\",\"notificationId\":100}"), response.proxy());

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.status);
    }

    @Test
    void sqlFailureReturnsSafe500() throws Exception {
        NotificationServlet.NotificationAllMutator allMutator = personnelId -> {
            throw new java.sql.SQLException("DB error");
        };
        NotificationServlet servlet = new NotificationServlet(null, null, null, allMutator, null);

        RecordingResponse response = new RecordingResponse();
        servlet.doPut(request(17, 1, "valid-token", "{\"action\":\"markAllRead\"}"), response.proxy());

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.status);
    }

    private static HttpServletRequest request(Integer personnelId, Integer roleId, String csrfToken, String body) {
        return requestWithParams(personnelId, roleId, csrfToken, body, Map.of());
    }

    private static HttpServletRequest requestWithMismatchCsrf(Integer personnelId, Integer roleId, String sessionToken, String headerToken, String body) {
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (personnelId != null) sessionAttributes.put("personnelId", personnelId);
        if (roleId != null) sessionAttributes.put("roleId", roleId);
        if (sessionToken != null) sessionAttributes.put("csrfToken", sessionToken);

        HttpSession session = personnelId == null ? null : proxy(HttpSession.class, (proxy, method, args) -> {
            if ("getAttribute".equals(method.getName())) return sessionAttributes.get(args[0]);
            return defaultValue(method.getReturnType());
        });

        return proxy(HttpServletRequest.class, (proxy, method, args) -> {
            if ("getSession".equals(method.getName())) return session;
            if ("getHeader".equals(method.getName())) {
                return "X-CSRF-Token".equals(args[0]) ? headerToken : null;
            }
            if ("getReader".equals(method.getName())) return new BufferedReader(new StringReader(body == null ? "" : body));
            return defaultValue(method.getReturnType());
        });
    }

    private static HttpServletRequest requestWithParams(Integer personnelId, Integer roleId, String csrfToken, String body, Map<String, String> params) {
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (personnelId != null) sessionAttributes.put("personnelId", personnelId);
        if (roleId != null) sessionAttributes.put("roleId", roleId);
        if (csrfToken != null) sessionAttributes.put("csrfToken", csrfToken);

        HttpSession session = personnelId == null ? null : proxy(HttpSession.class, (proxy, method, args) -> {
            if ("getAttribute".equals(method.getName())) return sessionAttributes.get(args[0]);
            return defaultValue(method.getReturnType());
        });

        return proxy(HttpServletRequest.class, (proxy, method, args) -> {
            if ("getSession".equals(method.getName())) return session;
            if ("getHeader".equals(method.getName())) {
                return "X-CSRF-Token".equals(args[0]) ? csrfToken : null;
            }
            if ("getParameter".equals(method.getName())) return params.get(args[0]);
            if ("getReader".equals(method.getName())) return new BufferedReader(new StringReader(body == null ? "" : body));
            return defaultValue(method.getReturnType());
        });
    }

    private static final class RecordingResponse {
        private int status;
        private final StringWriter output = new StringWriter();

        HttpServletResponse proxy() {
            return NotificationServletTest.proxy(HttpServletResponse.class, (target, method, args) -> {
                if ("setStatus".equals(method.getName())) {
                    status = (Integer) args[0];
                    return null;
                }
                if ("setContentType".equals(method.getName()) || "setCharacterEncoding".equals(method.getName()) || "setHeader".equals(method.getName())) {
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
