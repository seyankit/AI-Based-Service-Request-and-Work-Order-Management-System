package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HealthServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(HealthServlet.class.getName());
    private final Gson gson = new Gson();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        prepareJsonResponse(response);

        try (Connection connection = DatabaseConnection.getConnection()) {
            if (!connection.isValid(2)) {
                throw new IllegalStateException("The database connection did not validate.");
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(message(
                    true,
                    "Java backend is connected to MySQL."
            )));

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Database health check failed.", exception);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(gson.toJson(message(
                    false,
                    "Unable to connect to MySQL."
            )));
        }
    }

    private Map<String, Object> message(boolean success, String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("message", text);
        return result;
    }

    private void prepareJsonResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
