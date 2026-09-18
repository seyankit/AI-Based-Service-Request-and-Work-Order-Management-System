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

class ServiceRequestDraftServletTest {

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        RecordingResponse response = new RecordingResponse();
        ServiceRequestDraftServlet servlet = new ServiceRequestDraftServlet();

        servlet.doPost(request("{invalid", "application/json"), response.proxy());

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        assertTrue(response.body().contains("Malformed JSON request."));
    }

    @Test
    void invalidDraftReturnsConsistentValidationErrors() throws Exception {
        RecordingResponse response = new RecordingResponse();
        ServiceRequestDraftServlet servlet = new ServiceRequestDraftServlet();
        String body = "{\"title\":\"" + "x".repeat(151) + "\"}";

        servlet.doPost(request(body, "application/json"), response.proxy());

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        assertTrue(response.body().contains("Correct the highlighted request fields."));
        assertTrue(response.body().contains("\"errors\""));
        assertTrue(response.body().contains("\"title\""));
    }

    @Test
    void invalidFinalSubmissionReturnsBadRequest() throws Exception {
        RecordingResponse response = new RecordingResponse();
        ServiceRequestServlet servlet = new ServiceRequestServlet();

        servlet.doPost(request("{}", "application/json"), response.proxy());

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        assertTrue(response.body().contains("Correct the highlighted request fields."));
        assertTrue(response.body().contains("\"errors\""));
    }

    @Test
    void wrongContentTypeOnDraftPostReturnsUnsupportedMediaType() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestDraftServlet().doPost(
                request("{}", "text/plain"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, response.status);
        assertTrue(response.body().contains("Content-Type must be application/json."));
    }

    @Test
    void wrongContentTypeOnDraftPutReturnsUnsupportedMediaType() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestDraftServlet().doPut(
                request("{}", "text/plain", "9"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, response.status);
        assertTrue(response.body().contains("Content-Type must be application/json."));
    }

    @Test
    void oversizedDraftPostReturnsRequestEntityTooLarge() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestDraftServlet().doPost(
                request("x".repeat(32769), "application/json"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.status);
        assertTrue(response.body().contains("The request body is too large."));
    }

    @Test
    void oversizedDraftPutReturnsRequestEntityTooLarge() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestDraftServlet().doPut(
                request("x".repeat(32769), "application/json", "9"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.status);
        assertTrue(response.body().contains("The request body is too large."));
    }

    private static HttpServletRequest request(String body, String contentType) {
        return request(body, contentType, null);
    }

    private static HttpServletRequest request(
            String body,
            String contentType,
            String draftId
    ) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("personnelId", 17);
        attributes.put("roleId", 1);
        attributes.put("csrfToken", "csrf-test-token");
        HttpSession session = proxy(HttpSession.class, (proxy, method, args) -> {
            if ("getAttribute".equals(method.getName())) {
                return attributes.get(args[0]);
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(HttpServletRequest.class, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getSession" -> session;
                case "getContentType" -> contentType;
                case "getContentLengthLong" -> (long) body.length();
                case "getParameter" -> "id".equals(args[0]) ? draftId : null;
                case "getHeader" -> "X-CSRF-Token".equals(args[0])
                        ? "csrf-test-token" : null;
                case "getReader" -> new BufferedReader(new StringReader(body));
                default -> defaultValue(method.getReturnType());
            };
        });
    }

    private static final class RecordingResponse {
        private int status;
        private final StringWriter output = new StringWriter();

        HttpServletResponse proxy() {
            return ServiceRequestDraftServletTest.proxy(HttpServletResponse.class, (target, method, args) -> {
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
                new Class<?>[] {type},
                handler
        );
    }
}
