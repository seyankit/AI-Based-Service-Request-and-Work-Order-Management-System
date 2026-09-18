package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import ph.edu.htcgsc.serviceportal.dao.LoginSecurityDAO;
import ph.edu.htcgsc.serviceportal.dao.PersonnelDAO;
import ph.edu.htcgsc.serviceportal.model.LoginFailureState;
import ph.edu.htcgsc.serviceportal.model.Personnel;
import ph.edu.htcgsc.serviceportal.util.LoginSecurityPolicy;
import ph.edu.htcgsc.serviceportal.util.PasswordUtil;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LoginServlet.class.getName());
    private static final Set<Integer> APPLICATION_ROLE_IDS = Set.of(1, 2, 3, 4);
    private final Gson gson = new Gson();
    private PersonnelDAO personnelDAO = new PersonnelDAO();
    private LoginSecurityDAO loginSecurityDAO = new LoginSecurityDAO();

    public LoginServlet() {
    }

    // Visible for testing: lets focused servlet tests inject DAO mocks.
    LoginServlet(PersonnelDAO personnelDAO, LoginSecurityDAO loginSecurityDAO) {
        this.personnelDAO = personnelDAO;
        this.loginSecurityDAO = loginSecurityDAO;
    }

    private static class LoginRequest {
        String email;
        String password;
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        prepareJsonResponse(response);

        if (!isJsonRequest(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be application/json."
            );
            return;
        }

        try {
            LoginRequest data = gson.fromJson(request.getReader(), LoginRequest.class);

            if (data == null || isBlank(data.email) || isBlank(data.password)) {
                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Email and password are required."
                );
                return;
            }

            String email = data.email.trim().toLowerCase(Locale.ROOT);
            Personnel personnel = personnelDAO.findByEmail(email);

            if (personnel == null) {
                // Timing normalization: unknown emails run the same PBKDF2
                // cost as real accounts, so existence is not revealed.
                PasswordUtil.verifyDummyPassword(data.password);
                sendAuthenticationFailure(response);
                return;
            }

            // Verify the password before the lock check so that every
            // unauthenticated failure follows the same code path with the
            // same cost, and the externally visible response is identical
            // whether the email is unknown, the password is wrong, or the
            // account is locked.
            boolean passwordValid = PasswordUtil.verifyPassword(
                    data.password,
                    personnel.getPasswordHash()
            );

            LoginFailureState securityState =
                    loginSecurityDAO.findState(personnel.getPersonnelId());
            boolean accountLocked = LoginSecurityPolicy.isLocked(
                    securityState,
                    Instant.now()
            );

            if (!passwordValid) {
                if (!accountLocked) {
                    // Only unlocked accounts accumulate new failed attempts;
                    // a locked account is already at its maximum state.
                    loginSecurityDAO.recordFailedAttempt(
                            personnel.getPersonnelId(),
                            Instant.now()
                    );
                }
                sendAuthenticationFailure(response);
                return;
            }

            if (accountLocked) {
                // The lockout also blocks the correct password until it
                // expires. Externally this looks like any other failure.
                sendAuthenticationFailure(response);
                return;
            }

            if (!"Active".equalsIgnoreCase(personnel.getAccountStatus())) {
                String status = personnel.getAccountStatus() == null ? "" : personnel.getAccountStatus().trim();
                String message;
                if ("Pending Email Verification".equalsIgnoreCase(status)) {
                    message = "Please verify your school email before signing in.";
                } else if ("Pending Approval".equalsIgnoreCase(status) || "Pending".equalsIgnoreCase(status)) {
                    message = "Your email has been verified. Your account is awaiting authorization.";
                } else if ("Rejected".equalsIgnoreCase(status)) {
                    message = "This account registration was not approved.";
                } else if ("Suspended".equalsIgnoreCase(status) || "Disabled".equalsIgnoreCase(status) || "Inactive".equalsIgnoreCase(status)) {
                    message = "This account is not active.";
                } else {
                    message = "This account is not active.";
                }
                sendError(response, HttpServletResponse.SC_FORBIDDEN, message);
                return;
            }

            if (!APPLICATION_ROLE_IDS.contains(personnel.getRoleId())) {
                sendError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "This account does not have a valid system role assigned."
                );
                return;
            }

            // Every authorization check has passed: only now clear any
            // recorded failures and stamp the successful login time.
            loginSecurityDAO.resetOnSuccess(personnel.getPersonnelId(), Instant.now());

            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }

            HttpSession session = request.getSession(true);
            session.setAttribute("personnelId", personnel.getPersonnelId());
            session.setAttribute("roleId", personnel.getRoleId());
            session.setMaxInactiveInterval(30 * 60);
 
            Map<String, Object> result = authenticationResult(
                    personnel,
                    "Login successful."
            );

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result));

        } catch (JsonParseException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON request.");

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Login failed.", exception);
            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to process login."
            );
        }
    }

    private Map<String, Object> authenticationResult(
            Personnel personnel,
            String message) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("authenticated", true);
        result.put("message", message);
        result.put("personnelId", personnel.getPersonnelId());
        result.put("firstName", personnel.getFirstName());
        result.put("lastName", personnel.getLastName());
        result.put("email", personnel.getEmail());
        result.put("contactNumber", personnel.getContactNumber());
        result.put("personnelType", personnel.getPersonnelType());
        result.put("departmentId", personnel.getDepartmentId());
        result.put("departmentName", personnel.getDepartmentName());
        result.put("roleId", personnel.getRoleId());
        result.put(
            "profileImageFileName",
            personnel.getProfileImageFileName()
        );
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("application/json");
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String message) throws IOException {

        response.setStatus(status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("authenticated", false);
        result.put("message", message);
        response.getWriter().write(gson.toJson(result));
    }

    /**
     * The single externally visible shape for every unauthenticated
     * credential failure. Unknown emails, wrong passwords and active
     * lockouts all look exactly the same, so accounts cannot be
     * enumerated and lock state is not exposed.
     */
    private void sendAuthenticationFailure(HttpServletResponse response)
            throws IOException {

        sendError(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "Invalid email or password."
        );
    }

    private void prepareJsonResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
