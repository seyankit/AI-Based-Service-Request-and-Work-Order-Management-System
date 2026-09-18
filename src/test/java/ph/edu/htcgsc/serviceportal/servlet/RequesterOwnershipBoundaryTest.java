package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.dao.ServiceRequestQueryDAO;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequesterOwnershipBoundaryTest {

    @Test
    void detailRequiresAuthentication() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestServlet().doGet(
                request(null, null, null),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
        assertTrue(response.body().contains("Authentication is required."));
    }

    @Test
    void detailRequiresRequesterRole() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestServlet().doGet(
                request(17, 2, "42"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
        assertTrue(response.body().contains("Only requester accounts"));
    }

    @Test
    void detailRejectsNonPositiveRequestIdBeforeDatabaseAccess() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestServlet().doGet(
            request(17, 1, null, "0"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        assertTrue(response.body().contains("positive whole number"));
    }

    @Test
    void historyRequiresRequesterRole() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new RequestStatusHistoryServlet().doGet(
                request(17, 2, "42"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
        assertTrue(response.body().contains("Only requester accounts"));
    }

    @Test
    void historyRejectsNonPositiveRequestIdBeforeDatabaseAccess() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new RequestStatusHistoryServlet().doGet(
            request(17, 1, null, "0"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        assertTrue(response.body().contains("positive whole number"));
    }

    @Test
    void attachmentMetadataRejectsNonPositiveRequestIdBeforeDatabaseAccess()
            throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestAttachmentServlet().doGet(
                request(17, 1, null, "0"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        assertTrue(response.body().contains("valid service request ID"));
    }

    @Test
    void attachmentDownloadRejectsNonPositiveAttachmentIdBeforeDatabaseAccess()
            throws Exception {
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestAttachmentServlet().doGet(
                request(17, 1, "0", null),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        assertTrue(response.body().contains("valid attachment ID"));
    }

    @Test
    void queryRejectsInvalidRequesterAndRequestIdentifiersBeforeDatabaseAccess() {
        ServiceRequestQueryDAO dao = new ServiceRequestQueryDAO();

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.findByRequesterIdAndRequestId(0, 42)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> dao.findByRequesterIdAndRequestId(17, 0)
        );
    }

    private static HttpServletRequest request(
            Integer personnelId,
            Integer roleId,
            String attachmentId
    ) {
        return request(personnelId, roleId, attachmentId, null);
    }

    private static HttpServletRequest request(
            Integer personnelId,
            Integer roleId,
            String attachmentId,
            String requestId
    ) {
        Map<String, Object> attributes = new HashMap<>();
        if (personnelId != null) {
            attributes.put("personnelId", personnelId);
        }
        if (roleId != null) {
            attributes.put("roleId", roleId);
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
            if ("getParameter".equals(method.getName())) {
                return switch ((String) args[0]) {
                    case "attachmentId" -> attachmentId;
                    case "requestId" -> requestId;
                    default -> null;
                };
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static final class RecordingResponse {
        private int status;
        private final StringWriter output = new StringWriter();

        HttpServletResponse proxy() {
            return RequesterOwnershipBoundaryTest.proxy(
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
                    }
            );
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
