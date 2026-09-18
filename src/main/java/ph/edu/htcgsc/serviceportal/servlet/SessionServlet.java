package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import ph.edu.htcgsc.serviceportal.dao.PersonnelDAO;
import ph.edu.htcgsc.serviceportal.model.Personnel;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SessionServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(SessionServlet.class.getName());
    private static final Set<Integer> APPLICATION_ROLE_IDS = Set.of(1, 2, 3, 4);
    private final Gson gson = new Gson();
    private final PersonnelDAO personnelDAO = new PersonnelDAO();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        prepareJsonResponse(response);

        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("personnelId") instanceof Integer)) {
            sendUnauthenticated(response, "No active session.");
            return;
        }

        int personnelId = (Integer) session.getAttribute("personnelId");

        try {
            Personnel personnel = personnelDAO.findById(personnelId);

            if (personnel == null) {
                session.invalidate();
                sendUnauthenticated(response, "No active session.");
                return;
            }

            if (!"Active".equalsIgnoreCase(personnel.getAccountStatus())) {
                session.invalidate();
                sendError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "This account is not active."
                );
                return;
            }

            if (!APPLICATION_ROLE_IDS.contains(personnel.getRoleId())) {
                session.invalidate();
                sendError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "This account does not have a valid system role assigned."
                );
                return;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("authenticated", true);
            result.put("message", "Session restored.");
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

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result));

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Session restoration failed.", exception);
            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to restore the session."
            );
        }
    }

    private void sendUnauthenticated(
            HttpServletResponse response,
            String message) throws IOException {

        sendError(response, HttpServletResponse.SC_UNAUTHORIZED, message);
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
