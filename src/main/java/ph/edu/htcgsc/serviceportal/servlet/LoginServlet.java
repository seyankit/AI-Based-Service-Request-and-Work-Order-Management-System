package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import ph.edu.htcgsc.serviceportal.dao.PersonnelDAO;
import ph.edu.htcgsc.serviceportal.model.Personnel;
import ph.edu.htcgsc.serviceportal.util.PasswordUtil;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
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
    private final PersonnelDAO personnelDAO = new PersonnelDAO();

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

            if (personnel == null
                    || !PasswordUtil.verifyPassword(
                            data.password,
                            personnel.getPasswordHash())) {

                sendError(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Invalid email or password."
                );
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

    private void prepareJsonResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
