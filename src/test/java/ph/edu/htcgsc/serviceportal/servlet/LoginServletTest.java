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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused tests for the two confirmed Phase 1A security corrections:
 * 1. every unauthenticated failure returns the identical generic response
 *    (no account enumeration, no lock-state exposure), and
 * 2. the success reset runs only after all login authorization checks pass.
 */
class LoginServletTest {

    private static final String EMAIL = "user@online.htcgsc.edu.ph";
    private static final String PASSWORD = "Secret#123";
    private static final String GENERIC_FAILURE_BODY =
            "{\"success\":false,\"authenticated\":false,"
                    + "\"message\":\"Invalid email or password.\"}";
    private static final int PERSONNEL_ID = 7;

    private PersonnelDAO personnelDAO;
    private LoginSecurityDAO loginSecurityDAO;
    private LoginServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        personnelDAO = mock(PersonnelDAO.class);
        loginSecurityDAO = mock(LoginSecurityDAO.class);
        servlet = new LoginServlet(personnelDAO, loginSecurityDAO);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);

        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(
                new PrintWriter(responseBody, true)
        );
        when(request.getSession(anyBoolean())).thenReturn(session);
        stubLogin(EMAIL, PASSWORD);
    }

    private void stubLogin(String email, String password) throws IOException {
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"
        )));
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

    private void assertGenericFailure() throws Exception {
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals(GENERIC_FAILURE_BODY, responseBody.toString());
        verify(response, never()).setHeader(eq("Retry-After"), anyString());
        verify(loginSecurityDAO, never()).resetOnSuccess(anyInt(), any());
    }

    @Test
    void unknownEmailReturnsTheIdenticalGenericFailure() throws Exception {
        when(personnelDAO.findByEmail(EMAIL)).thenReturn(null);

        servlet.doPost(request, response);

        assertGenericFailure();
        verify(loginSecurityDAO, never()).recordFailedAttempt(anyInt(), any());
    }

    @Test
    void wrongPasswordRecordsAttemptAndReturnsTheIdenticalGenericFailure()
            throws Exception {

        when(personnelDAO.findByEmail(EMAIL)).thenReturn(activePersonnelWithRole(2));
        when(loginSecurityDAO.findState(PERSONNEL_ID)).thenReturn(null);
        stubLogin(EMAIL, "Wrong#456");

        servlet.doPost(request, response);

        assertGenericFailure();
        verify(loginSecurityDAO).recordFailedAttempt(eq(PERSONNEL_ID), any(Instant.class));
    }

    @Test
    void lockedAccountWithCorrectPasswordIsBlockedLikeAnyOtherFailure()
            throws Exception {

        when(personnelDAO.findByEmail(EMAIL)).thenReturn(activePersonnelWithRole(2));
        when(loginSecurityDAO.findState(PERSONNEL_ID)).thenReturn(activeLockState());
        // Correct password on purpose: the lock must still block it.

        servlet.doPost(request, response);

        assertGenericFailure();
        verify(loginSecurityDAO, never()).recordFailedAttempt(anyInt(), any());
    }

    @Test
    void lockedAccountWithWrongPasswordIsBlockedWithoutExtraRecording()
            throws Exception {

        when(personnelDAO.findByEmail(EMAIL)).thenReturn(activePersonnelWithRole(2));
        when(loginSecurityDAO.findState(PERSONNEL_ID)).thenReturn(activeLockState());
        stubLogin(EMAIL, "Wrong#456");

        servlet.doPost(request, response);

        assertGenericFailure();
        verify(loginSecurityDAO, never()).recordFailedAttempt(anyInt(), any());
    }

    @Test
    void resetIsSkippedWhenAccountIsNotActive() throws Exception {
        Personnel pending = activePersonnelWithRole(2);
        pending.setAccountStatus("Pending Approval");
        when(personnelDAO.findByEmail(EMAIL)).thenReturn(pending);
        when(loginSecurityDAO.findState(PERSONNEL_ID)).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertTrue(responseBody.toString().contains("awaiting authorization"));
        verify(loginSecurityDAO, never()).resetOnSuccess(anyInt(), any());
        verify(session, never()).setAttribute(anyString(), any());
    }

    @Test
    void resetIsSkippedWhenRoleIsInvalid() throws Exception {
        when(personnelDAO.findByEmail(EMAIL)).thenReturn(activePersonnelWithRole(9));
        when(loginSecurityDAO.findState(PERSONNEL_ID)).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertTrue(responseBody.toString().contains("valid system role"));
        verify(loginSecurityDAO, never()).resetOnSuccess(anyInt(), any());
        verify(session, never()).setAttribute(anyString(), any());
    }

    @Test
    void resetRunsOnlyAfterAllAuthorizationChecksOnSuccess() throws Exception {
        when(personnelDAO.findByEmail(EMAIL)).thenReturn(activePersonnelWithRole(2));
        when(loginSecurityDAO.findState(PERSONNEL_ID)).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseBody.toString().contains("\"success\":true"));
        assertTrue(responseBody.toString().contains("Login successful"));

        // The reset must happen before the new session is populated.
        var order = inOrder(loginSecurityDAO, session);
        order.verify(loginSecurityDAO).resetOnSuccess(eq(PERSONNEL_ID), any(Instant.class));
        order.verify(session).setAttribute(eq("personnelId"), eq(PERSONNEL_ID));
    }
}
