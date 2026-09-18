package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class LogoutServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        prepareJsonResponse(response);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("authenticated", false);
        result.put("message", "Logout successful.");

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(gson.toJson(result));
    }

    private void prepareJsonResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
