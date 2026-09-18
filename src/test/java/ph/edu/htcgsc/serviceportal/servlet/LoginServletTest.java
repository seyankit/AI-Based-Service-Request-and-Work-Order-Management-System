package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.dao.LoginSecurityDAO;
import ph.edu.htcgsc.serviceportal.dao.PersonnelDAO;
import ph.edu.htcgsc.serviceportal.model.LoginFailureState;
import ph.edu.htcgsc.serviceportal.model.Personnel;
import ph.edu.htcgsc.serviceportal.util.LoginSecurityPolicy;
import ph.edu.htcgsc.serviceportal.util.PasswordUtil;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the two confirmed Phase 1A security corrections:
 * 1. every unauthenticated failure returns the identical generic response
 *    (no account enumeration, no lock-state exposure), and
 * 2. the success reset runs only after all login authorization checks pass.
 *
 * Uses hand-rolled stubs and JDK proxies so no extra test dependency is
 * required; the stubbed DAOs subclass the real DAO classes.
 */
class LoginServletTest {

    private static final String EMAIL = "user@online.htcgsc.edu.ph";
    private static final String PASSWORD = "Secret#123";
    private static final String GENERIC_FAILURE_BODY =
            "{\"success\":false,\"authenticated\":false,"
                    + "\"message\":\"Invalid email or password.\"}";
    private static final int PERSONNEL_ID = 7;
    private static final Set<String> EXPECTED_FAILURE_HEADERS =
            Set.of("Cache-Control", "X-Content-Type-Options");

    /** Overrides only the lookup used by the login flow. */
    private static final class StubPersonnelDAO extends PersonnelDAO {
        Personnel byEmail;

        @Override
        public Personnel findByEmail(String email) {
            return byEmail;
        }
    }

    /** Overrides the login-security operations without touching MySQL. */
    private static final class StubLoginSecurityDAO extends LoginSecurityDAO {
        LoginFailureState stateToReturn;
        final List<String> trace;

        StubLoginSecurityDAO(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public LoginFailureState findState(int personnelId) {
            return stateToReturn;
        }

        @Override
        public LoginFailureState recordFailedAttempt(int personnelId, Instant now) {
            trace.add("recordFailedAttempt");
            return null;
        }

        @Override
        public void resetOnSuccess(int personnelId, Instant now) {
            trace.add("resetOnSuccess");
        }
    }

    /** Records status, headers and body written by the servlet. */
    private static final class RecordingResponse {
        Integer status;
        final Map<String, String> headers = new LinkedHashMap<>();
        final StringWriter body = new StringWriter();
        private PrintWriter writer;

        HttpServletResponse proxy() {
            return LoginServletTest.proxy(HttpServletResponse.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "setStatus":
                        status = (Integer) args[0];
                        return null;
                    case "setHeader":
                        headers.put((String) args[0], (String) args[1]);
                        return null;
                    case "getWriter":
                        if (writer == null) {
                            writer = new PrintWriter(body, true);
                        }
                        return writer;
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
        }

        String text() {
            return body.toString();
        }

        String header(String name) {
            return headers.get(name);
        }
    }

    private final List<String> trace = new ArrayList<>();
    private final StubPersonnelDAO personnelDAO = new StubPersonnelDAO();
    private final StubLoginSecurityDAO loginSecurityDAO =
            new StubLoginSecurityDAO(trace);
    private final LoginServlet servlet =
            new LoginServlet(personnelDAO, loginSecurityDAO);
    private final RecordingResponse recording = new RecordingResponse();
    private final HttpServletResponse response = recording.proxy();

    private HttpServletRequest request;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        session = newSession();
        login(EMAIL, PASSWORD);
    }

    private void login(String email, String password) {
        String json = "{\"email\":\"" + email + "\",\"password\":\""
                + password + "\"}";
        request = proxy(HttpServletRequest.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getContentType":
                    return "application/json";
                case "getReader":
                    return new BufferedReader(new StringReader(json));
                case "getSession":
                    return session;
                default:
                    return defaultValue(method.getReturnType());
            }
        });
    }

    private HttpSession newSession() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        return proxy(HttpSession.class, (p, method, args) -> {
            switch (method.getName()) {
                case "setAttribute":
                    trace.add("session:" + args[0]);
                    attributes.put((String) args[0], args[1]);
                    return null;
                case "getAttribute":
                    return attributes.get(args[0]);
                default:
                    return defaultValue(method.getReturnType());
            }
        });
    }

    private Personnel activePersonnelWithRole(int roleId) {
        Personnel personnel = new Personnel();
        personnel.setPersonnelId(PERSONNEL_ID);
        personnel.setEmail(EMAIL);
        personnel.setPasswordHash(PasswordUtil.hashPassword(PASSWORD));
        personnel.setAccountStatus("Active");
        personnel.setRoleId(roleId);
        personnel.setDepartmentId(3);
        return personnel;
    }

    private LoginFailureState activeLockState() {
        Instant now = Instant.now();
        return new LoginFailureState(
                LoginSecurityPolicy.MAX_FAILED_ATTEMPTS,
                now,
                now,
                LoginSecurityPolicy.lockExpiry(now)
        );
    }

    private void assertGenericFailure() {
        assertEquals(Integer.valueOf(HttpServletResponse.SC_UNAUTHORIZED),
                recording.status);
        assertEquals(GENERIC_FAILURE_BODY, recording.text());
        assertNull(recording.header("Retry-After"));
        assertEquals(EXPECTED_FAILURE_HEADERS, recording.headers.keySet());
        assertFalse(trace.contains("resetOnSuccess"));
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
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

    @Test
    void unknownEmailReturnsTheIdenticalGenericFailure() throws Exception {
        personnelDAO.byEmail = null;

        servlet.doPost(request, response);

        assertGenericFailure();
        assertFalse(trace.contains("recordFailedAttempt"));
    }

    @Test
    void wrongPasswordRecordsAttemptAndReturnsTheIdenticalGenericFailure()
            throws Exception {

        personnelDAO.byEmail = activePersonnelWithRole(2);
        loginSecurityDAO.stateToReturn = null;
        login(EMAIL, "Wrong#456");

        servlet.doPost(request, response);

        assertGenericFailure();
        assertTrue(trace.contains("recordFailedAttempt"));
    }

    @Test
    void lockedAccountWithCorrectPasswordIsBlockedLikeAnyOtherFailure()
            throws Exception {

        personnelDAO.byEmail = activePersonnelWithRole(2);
        loginSecurityDAO.stateToReturn = activeLockState();
        // Correct password on purpose: the lock must still block it.

        servlet.doPost(request, response);

        assertGenericFailure();
        assertFalse(trace.contains("recordFailedAttempt"));
    }

    @Test
    void lockedAccountWithWrongPasswordIsBlockedWithoutExtraRecording()
            throws Exception {

        personnelDAO.byEmail = activePersonnelWithRole(2);
        loginSecurityDAO.stateToReturn = activeLockState();
        login(EMAIL, "Wrong#456");

        servlet.doPost(request, response);

        assertGenericFailure();
        assertFalse(trace.contains("recordFailedAttempt"));
    }

    @Test
    void resetIsSkippedWhenAccountIsNotActive() throws Exception {
        Personnel pending = activePersonnelWithRole(2);
        pending.setAccountStatus("Pending Approval");
        personnelDAO.byEmail = pending;
        loginSecurityDAO.stateToReturn = null;

        servlet.doPost(request, response);

        assertEquals(Integer.valueOf(HttpServletResponse.SC_FORBIDDEN),
                recording.status);
        assertTrue(recording.text().contains("awaiting authorization"));
        assertFalse(trace.contains("resetOnSuccess"));
        assertFalse(trace.stream().anyMatch(entry -> entry.startsWith("session:")));
    }

    @Test
    void resetIsSkippedWhenRoleIsInvalid() throws Exception {
        personnelDAO.byEmail = activePersonnelWithRole(9);
        loginSecurityDAO.stateToReturn = null;

        servlet.doPost(request, response);

        assertEquals(Integer.valueOf(HttpServletResponse.SC_FORBIDDEN),
                recording.status);
        assertTrue(recording.text().contains("valid system role"));
        assertFalse(trace.contains("resetOnSuccess"));
        assertFalse(trace.stream().anyMatch(entry -> entry.startsWith("session:")));
    }

    @Test
    void resetRunsOnlyAfterAllAuthorizationChecksOnSuccess() throws Exception {
        personnelDAO.byEmail = activePersonnelWithRole(2);
        loginSecurityDAO.stateToReturn = null;

        servlet.doPost(request, response);

        assertEquals(Integer.valueOf(HttpServletResponse.SC_OK), recording.status);
        assertTrue(recording.text().contains("\"success\":true"));
        assertTrue(recording.text().contains("Login successful"));

        // The reset must run after the status and role checks passed and
        // before the new session is populated.
        assertEquals(
                List.of("resetOnSuccess", "session:personnelId", "session:roleId"),
                trace
        );
    }
}
