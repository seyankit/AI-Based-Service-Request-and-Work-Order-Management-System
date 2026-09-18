package ph.edu.htcgsc.serviceportal.util;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/** Common response helpers; ApiSecurityFilter bounds and validates JSON before dispatch. */
public final class ApiJson {
    private static final Gson GSON = new Gson();
    private ApiJson() { }

    public static <T> T read(HttpServletRequest request, Class<T> type) throws IOException {
        T result = GSON.fromJson(request.getReader(), type);
        if (result == null) throw new JsonParseException("A JSON object is required.");
        return result;
    }

    public static void send(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write(GSON.toJson(body));
    }

    public static void error(HttpServletResponse response, int status, String message) throws IOException {
        send(response, status, Map.of("success", false, "message", message));
    }
}
