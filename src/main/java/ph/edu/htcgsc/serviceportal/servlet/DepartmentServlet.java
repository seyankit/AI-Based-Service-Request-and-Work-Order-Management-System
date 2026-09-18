package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import ph.edu.htcgsc.serviceportal.dao.DepartmentDAO;
import ph.edu.htcgsc.serviceportal.model.Department;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DepartmentServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(DepartmentServlet.class.getName());
    private final Gson gson = new Gson();
    private final DepartmentDAO departmentDAO = new DepartmentDAO();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        prepareJsonResponse(response);

        try {
            List<Department> departments = departmentDAO.findAll();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("departments", departments);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result));

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Unable to retrieve departments.", exception);
            sendError(response, "Unable to load departments.");
        }
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().write(gson.toJson(result));
    }

    private void prepareJsonResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
