package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class CsrfTokenServlet extends HttpServlet {

    private static final Gson GSON =
            new Gson();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        prepareJsonResponse(response);

        if (!SessionUtil.isAuthenticated(request)) {
            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
            );

            writeResponse(
                    response,
                    false,
                    "Authentication is required.",
                    null
            );

            return;
        }

        String csrfToken =
                CsrfUtil.getOrCreateToken(request);

        if (csrfToken == null) {
            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
            );

            writeResponse(
                    response,
                    false,
                    "The authenticated session is no longer available.",
                    null
            );

            return;
        }

        response.setStatus(
                HttpServletResponse.SC_OK
        );

        writeResponse(
                response,
                true,
                "CSRF token generated successfully.",
                csrfToken
        );
    }

    private void prepareJsonResponse(
            HttpServletResponse response
    ) {
        response.setContentType(
                "application/json"
        );

        response.setCharacterEncoding(
                "UTF-8"
        );

        response.setHeader(
                "Cache-Control",
                "no-store, no-cache, must-revalidate"
        );

        response.setHeader(
                "Pragma",
                "no-cache"
        );
    }

    private void writeResponse(
            HttpServletResponse response,
            boolean success,
            String message,
            String csrfToken
    ) throws IOException {

        Map<String, Object> responseBody =
                new LinkedHashMap<>();

        responseBody.put(
                "success",
                success
        );

        responseBody.put(
                "message",
                message
        );

        if (csrfToken != null) {
            responseBody.put(
                    "headerName",
                    CsrfUtil.HEADER_NAME
            );

            responseBody.put(
                    "csrfToken",
                    csrfToken
            );
        }

        response.getWriter().write(
                GSON.toJson(responseBody)
        );
    }
}