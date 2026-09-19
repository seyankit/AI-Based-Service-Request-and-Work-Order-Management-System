package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.dao.WorkOrderDAO;

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

class WorkOrderServletTest {

    @Test
    void unauthenticatedAccessIsRejected() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new WorkOrderServlet().doGet(request(null, null, null, null), response.proxy());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void nonAdministratorRolesAreForbidden() throws Exception {
        for (int role : new int[] { 1, 3, 4 }) {
            RecordingResponse getResponse = new RecordingResponse();
            new WorkOrderServlet().doGet(request(17, role, null, null), getResponse.proxy());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, getResponse.status);

            RecordingResponse postResponse = new RecordingResponse();
            new WorkOrderServlet().doPost(
                    request(17, role, "csrf-token",
                            "{\"requestId\":1,\"workDescription\":\"Replace the damaged network cable.\"}"),
                    postResponse.proxy());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, postResponse.status);

            RecordingResponse putResponse = new RecordingResponse();
            new WorkOrderServlet().doPut(
                    request(17, role, "csrf-token",
                            "{\"action\":\"assign\",\"workOrderId\":1,\"technicianId\":4}"),
                    putResponse.proxy());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, putResponse.status);
        }
    }

    @Test
    void unauthenticatedAssignmentIsRejected() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new WorkOrderServlet().doPut(
                request(null, null, null,
                        "{\"action\":\"assign\",\"workOrderId\":1,\"technicianId\":4}"),
                response.proxy());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void administratorAssignmentUsesSessionIdentityAndExactInput() throws Exception {
        RecordingResponse response = new RecordingResponse();
        WorkOrderDAO.AssignmentInput[] captured = { null };
        int[] actorAndRole = { 0, 0 };
        WorkOrderServlet servlet = new WorkOrderServlet(
                (actor, role, workOrderId) -> List.of(),
                (actor, role, input) -> Map.of(),
                (actor, role, input) -> {
                    actorAndRole[0] = actor;
                    actorAndRole[1] = role;
                    captured[0] = input;
                    return Map.of("workOrderId", 12, "status", "Assigned");
                });

        servlet.doPut(request(17, 2, "csrf-token",
                "{\"action\":\"assign\",\"workOrderId\":12,\"technicianId\":44,"
                        + "\"assignmentNotes\":\"  Replace switch  \",\"actorId\":999,"
                        + "\"status\":\"Completed\",\"departmentId\":88}"), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertEquals(17, actorAndRole[0]);
        assertEquals(2, actorAndRole[1]);
        assertEquals(12L, captured[0].workOrderId());
        assertEquals(44, captured[0].technicianId());
        assertEquals("Replace switch", captured[0].assignmentNotes());
        assertTrue(response.body().contains("Assigned"));
    }

    @Test
    void assignmentValidationRejectsMalformedActionAndIdentifiers() throws Exception {
        for (String body : new String[] {
                "{invalid",
                "{\"action\":\"reject\",\"workOrderId\":1,\"technicianId\":4}",
                "{\"action\":\"assign\",\"workOrderId\":0,\"technicianId\":4}",
                "{\"action\":\"assign\",\"workOrderId\":1,\"technicianId\":0}",
                "{\"action\":\"assign\",\"workOrderId\":1,\"technicianId\":4,\"assignmentNotes\":\"x\"}"
        }) {
            RecordingResponse response = new RecordingResponse();
            new WorkOrderServlet(
                    (actor, role, workOrderId) -> List.of(),
                    (actor, role, input) -> Map.of(),
                    (actor, role, input) -> Map.of()).doPut(
                    request(17, 2, "csrf-token", body), response.proxy());
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        }
    }

    @Test
    void assignmentSecurityChecksRejectMissingCsrfAndWrongContentType() throws Exception {
        String body = "{\"action\":\"assign\",\"workOrderId\":1,\"technicianId\":4}";
        RecordingResponse csrf = new RecordingResponse();
        new WorkOrderServlet().doPut(request(17, 2, null, body), csrf.proxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, csrf.status);

        RecordingResponse contentType = new RecordingResponse();
        new WorkOrderServlet().doPut(request(17, 2, "csrf-token", body, "text/plain"), contentType.proxy());
        assertEquals(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, contentType.status);
    }

    @Test
    void assignmentFailuresMapToNotFoundAndConflict() throws Exception {
        for (WorkOrderDAO.AssignmentFailure failure : new WorkOrderDAO.AssignmentFailure[] {
                WorkOrderDAO.AssignmentFailure.WORK_ORDER_NOT_FOUND,
                WorkOrderDAO.AssignmentFailure.TECHNICIAN_NOT_FOUND
        }) {
            RecordingResponse response = new RecordingResponse();
            new WorkOrderServlet(
                    (actor, role, workOrderId) -> List.of(),
                    (actor, role, input) -> Map.of(),
                    (actor, role, input) -> {
                        throw new WorkOrderDAO.AssignmentException(failure, "Assignment rejected.");
                    }).doPut(request(17, 2, "csrf-token",
                    "{\"action\":\"assign\",\"workOrderId\":1,\"technicianId\":4}"), response.proxy());
            assertEquals(HttpServletResponse.SC_NOT_FOUND, response.status);
        }

        for (WorkOrderDAO.AssignmentFailure failure : new WorkOrderDAO.AssignmentFailure[] {
                WorkOrderDAO.AssignmentFailure.WORK_ORDER_NOT_CREATED,
                WorkOrderDAO.AssignmentFailure.ALREADY_ASSIGNED,
                WorkOrderDAO.AssignmentFailure.TECHNICIAN_NOT_ELIGIBLE,
                WorkOrderDAO.AssignmentFailure.TECHNICIAN_DEPARTMENT_MISMATCH
        }) {
            RecordingResponse response = new RecordingResponse();
            new WorkOrderServlet(
                    (actor, role, workOrderId) -> List.of(),
                    (actor, role, input) -> Map.of(),
                    (actor, role, input) -> {
                        throw new WorkOrderDAO.AssignmentException(failure, "Assignment rejected.");
                    }).doPut(request(17, 2, "csrf-token",
                    "{\"action\":\"assign\",\"workOrderId\":1,\"technicianId\":4}"), response.proxy());
            assertEquals(HttpServletResponse.SC_CONFLICT, response.status);
        }
    }

    @Test
    void administratorGetIsAllowed() throws Exception {
        RecordingResponse response = new RecordingResponse();
        boolean[] called = { false };
        WorkOrderServlet servlet = new WorkOrderServlet(
                (actor, role, workOrderId) -> {
                    called[0] = actor == 17 && role == 2 && workOrderId == null;
                    return List.of(Map.of("workOrderId", 1));
                },
                (actor, role, input) -> Map.of());

        servlet.doGet(request(17, 2, null, null), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertTrue(called[0]);
        assertTrue(response.body().contains("workOrders"));
    }

    @Test
    void administratorPostIsAllowedAndUsesSessionIdentity() throws Exception {
        RecordingResponse response = new RecordingResponse();
        boolean[] called = { false };
        WorkOrderServlet servlet = new WorkOrderServlet(
                (actor, role, workOrderId) -> List.of(),
                (actor, role, input) -> {
                    called[0] = actor == 17
                            && role == 2
                            && input.requestId() == 42L
                            && "Repair the damaged network cable.".equals(input.workDescription());
                    return Map.of("workOrderId", 9, "status", "Created");
                });

        servlet.doPost(request(17, 2, "csrf-token",
                "{\"requestId\":42,\"workDescription\":\" Repair the damaged network cable. \","
                        + "\"createdBy\":999,\"roleId\":4,\"departmentId\":99,\"status\":\"Completed\"}"),
                response.proxy());

        assertEquals(HttpServletResponse.SC_CREATED, response.status);
        assertTrue(called[0]);
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        RecordingResponse response = new RecordingResponse();

        new WorkOrderServlet().doPost(
                request(17, 2, "csrf-token", "{invalid"),
                response.proxy());

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    }

    @Test
    void invalidAndMissingRequestIdReturnBadRequest() throws Exception {
        for (String body : new String[] {
                "{\"requestId\":0,\"workDescription\":\"Valid work description.\"}",
                "{\"workDescription\":\"Valid work description.\"}"
        }) {
            RecordingResponse response = new RecordingResponse();
            new WorkOrderServlet(
                    (actor, role, workOrderId) -> List.of(),
                    (actor, role, input) -> Map.of()).doPost(
                    request(17, 2, "csrf-token", body), response.proxy());
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
        }
    }

    @Test
    void unsupportedContentTypeAndMissingCsrfAreRejected() throws Exception {
        RecordingResponse unsupported = new RecordingResponse();
        new WorkOrderServlet().doPost(
                request(17, 2, "csrf-token",
                        "{\"requestId\":1,\"workDescription\":\"Valid work description.\"}",
                        "text/plain"),
                unsupported.proxy());
        assertEquals(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, unsupported.status);

        RecordingResponse csrf = new RecordingResponse();
        new WorkOrderServlet().doPost(
                request(17, 2, null,
                        "{\"requestId\":1,\"workDescription\":\"Valid work description.\"}"),
                csrf.proxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, csrf.status);
    }

    @Test
    void creationFailuresMapToNotFoundAndConflict() throws Exception {
        for (WorkOrderDAO.CreationFailure failure : new WorkOrderDAO.CreationFailure[] {
                WorkOrderDAO.CreationFailure.REQUEST_NOT_APPROVED,
                WorkOrderDAO.CreationFailure.DUPLICATE_WORK_ORDER
        }) {
            RecordingResponse response = new RecordingResponse();
            WorkOrderServlet servlet = new WorkOrderServlet(
                    (actor, role, workOrderId) -> List.of(),
                    (actor, role, input) -> {
                        throw new WorkOrderDAO.CreationException(failure, "Creation rejected.");
                    });
            servlet.doPost(request(17, 2, "csrf-token",
                    "{\"requestId\":1,\"workDescription\":\"Valid work description.\"}"),
                    response.proxy());
            assertEquals(HttpServletResponse.SC_CONFLICT, response.status);
        }

        RecordingResponse notFound = new RecordingResponse();
        WorkOrderServlet servlet = new WorkOrderServlet(
                (actor, role, workOrderId) -> List.of(),
                (actor, role, input) -> {
                    throw new WorkOrderDAO.CreationException(
                            WorkOrderDAO.CreationFailure.REQUEST_NOT_FOUND,
                            "The requested service request was not found.");
                });
        servlet.doPost(request(17, 2, "csrf-token",
                "{\"requestId\":1,\"workDescription\":\"Valid work description.\"}"),
                notFound.proxy());
        assertEquals(HttpServletResponse.SC_NOT_FOUND, notFound.status);
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
        if (personnelId != null) attributes.put("personnelId", personnelId);
        if (roleId != null) attributes.put("roleId", roleId);
        if (csrfToken != null) attributes.put("csrfToken", csrfToken);

        HttpSession session = personnelId == null
                ? null
                : proxy(HttpSession.class, (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) return attributes.get(args[0]);
                    return defaultValue(method.getReturnType());
                });

        return proxy(HttpServletRequest.class, (proxy, method, args) -> {
            if ("getSession".equals(method.getName())) return session;
            if ("getHeader".equals(method.getName())) {
                return "X-CSRF-Token".equals(args[0]) ? csrfToken : null;
            }
            if ("getContentType".equals(method.getName())) return contentType;
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
            return WorkOrderServletTest.proxy(
                    HttpServletResponse.class,
                    (target, method, args) -> {
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
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] { type },
                handler);
    }
}
