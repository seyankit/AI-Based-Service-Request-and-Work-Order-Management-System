package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.dao.ServiceRequestDAO;
import ph.edu.htcgsc.serviceportal.dto.CreateServiceRequestRequest;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRequestCrudServletTest {

    @Test
    void updateRequiresAuthentication() throws Exception {
        StubDao dao = new StubDao();
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestServlet(dao).doPut(
                request(null, null, validUpdateBody(), null),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
        assertFalse(dao.updated);
    }

    @Test
    void updateRejectsInvalidInputBeforeDao() throws Exception {
        StubDao dao = new StubDao();
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestServlet(dao).doPut(
                request(1, "csrf-test-token", "{\"requestId\":7}", null),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        assertFalse(dao.updated);
    }

    @Test
    void ownerCanUpdateEligibleRequest() throws Exception {
        StubDao dao = new StubDao();
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestServlet(dao).doPut(
                request(1, "csrf-test-token", validUpdateBody(), null),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(dao.updated);
        assertEquals(7L, dao.requestId);
        assertEquals(2, dao.request.getRequestedCategoryId());
        assertEquals("High", dao.request.getPreferredPriority());
    }

    @Test
    void updateMapsForeignAndProtectedRequests() throws Exception {
        assertUpdateFailure(
                ServiceRequestDAO.MutationFailureReason.REQUEST_NOT_OWNED,
                HttpServletResponse.SC_FORBIDDEN
        );
        assertUpdateFailure(
                ServiceRequestDAO.MutationFailureReason.REQUEST_NOT_EDITABLE,
                HttpServletResponse.SC_CONFLICT
        );
        assertUpdateFailure(
                ServiceRequestDAO.MutationFailureReason.REQUEST_NOT_FOUND,
                HttpServletResponse.SC_NOT_FOUND
        );
    }

    @Test
    void ownerCanDeleteEligibleRequestWithoutProtectedDependencies() throws Exception {
        StubDao dao = new StubDao();
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestServlet(dao).doDelete(
                request(1, "csrf-test-token", "", "7"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(dao.deleted);
        assertEquals(7L, dao.requestId);
    }

    @Test
    void deleteMapsForeignProtectedAndMissingRequests() throws Exception {
        assertDeleteFailure(
                ServiceRequestDAO.MutationFailureReason.REQUEST_NOT_OWNED,
                HttpServletResponse.SC_FORBIDDEN
        );
        assertDeleteFailure(
                ServiceRequestDAO.MutationFailureReason.PROTECTED_DEPENDENCIES,
                HttpServletResponse.SC_CONFLICT
        );
        assertDeleteFailure(
                ServiceRequestDAO.MutationFailureReason.REQUEST_NOT_EDITABLE,
                HttpServletResponse.SC_CONFLICT
        );
        assertDeleteFailure(
                ServiceRequestDAO.MutationFailureReason.REQUEST_NOT_FOUND,
                HttpServletResponse.SC_NOT_FOUND
        );
    }

    @Test
    void deleteRejectsMissingCsrfBeforeDao() throws Exception {
        StubDao dao = new StubDao();
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestServlet(dao).doDelete(
                request(1, null, "", "7"),
                response.proxy()
        );

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
        assertFalse(dao.deleted);
    }

    private static void assertUpdateFailure(
            ServiceRequestDAO.MutationFailureReason reason,
            int expectedStatus
    ) throws Exception {
        StubDao dao = new StubDao();
        dao.updateFailure = new ServiceRequestDAO.MutationException(reason, "Denied.");
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestServlet(dao).doPut(
                request(1, "csrf-test-token", validUpdateBody(), null),
                response.proxy()
        );

        assertEquals(expectedStatus, response.status);
    }

    private static void assertDeleteFailure(
            ServiceRequestDAO.MutationFailureReason reason,
            int expectedStatus
    ) throws Exception {
        StubDao dao = new StubDao();
        dao.deleteFailure = new ServiceRequestDAO.MutationException(reason, "Denied.");
        RecordingResponse response = new RecordingResponse();

        new ServiceRequestServlet(dao).doDelete(
                request(1, "csrf-test-token", "", "7"),
                response.proxy()
        );

        assertEquals(expectedStatus, response.status);
    }

    private static String validUpdateBody() {
        return "{\"requestId\":7,\"requestedCategoryId\":2,"
                + "\"preferredPriority\":\"High\","
                + "\"title\":\"Network connection failure\","
                + "\"description\":\"The laboratory network connection is unavailable.\","
                + "\"location\":\"Laboratory 2\","
                + "\"dateReported\":\"2026-09-21\"}";
    }

    private static HttpServletRequest request(
            Integer roleId,
            String csrfHeader,
            String body,
            String requestId
    ) {
        Map<String, Object> attributes = new HashMap<>();
        if (roleId != null) {
            attributes.put("personnelId", 17);
            attributes.put("roleId", roleId);
            attributes.put("csrfToken", "csrf-test-token");
        }

        HttpSession session = roleId == null ? null : proxy(
                HttpSession.class,
                (target, method, args) -> "getAttribute".equals(method.getName())
                        ? attributes.get(args[0])
                        : defaultValue(method.getReturnType())
        );

        return proxy(HttpServletRequest.class, (target, method, args) -> switch (method.getName()) {
            case "getSession" -> session;
            case "getContentType" -> "application/json";
            case "getContentLengthLong" -> (long) body.length();
            case "getParameter" -> "requestId".equals(args[0]) ? requestId : null;
            case "getHeader" -> "X-CSRF-Token".equals(args[0]) ? csrfHeader : null;
            case "getReader" -> new BufferedReader(new StringReader(body));
            case "getRemoteAddr" -> "127.0.0.1";
            default -> defaultValue(method.getReturnType());
        });
    }

    private static final class StubDao extends ServiceRequestDAO {
        private boolean updated;
        private boolean deleted;
        private long requestId;
        private CreateServiceRequestRequest request;
        private MutationException updateFailure;
        private MutationException deleteFailure;

        @Override
        public void updateServiceRequest(
                long requestId,
                CreateServiceRequestRequest request,
                int authenticatedPersonnelId,
                String clientIpAddress,
                String clientUserAgent
        ) throws SQLException, CreationException, MutationException {
            if (updateFailure != null) throw updateFailure;
            this.updated = true;
            this.requestId = requestId;
            this.request = request;
        }

        @Override
        public void deleteServiceRequest(
                long requestId,
                int authenticatedPersonnelId,
                String clientIpAddress,
                String clientUserAgent
        ) throws SQLException, CreationException, MutationException {
            if (deleteFailure != null) throw deleteFailure;
            this.deleted = true;
            this.requestId = requestId;
        }
    }

    private static final class RecordingResponse {
        private int status;
        private final StringWriter output = new StringWriter();

        HttpServletResponse proxy() {
            return ServiceRequestCrudServletTest.proxy(
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
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                handler
        );
    }
}
