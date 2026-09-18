package ph.edu.htcgsc.serviceportal.filter;

import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.util.ApiJson;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Every API request rechecks account status and role; UI state is never authorization. */
@WebFilter("/api/*")
public final class ApiSecurityFilter implements Filter {
    private static final Logger LOG = Logger.getLogger(ApiSecurityFilter.class.getName());
    public static final int MAX_JSON_BYTES = 65536;
    private static final Set<String> PUBLIC = Set.of("/api/login", "/api/register", "/api/departments",
            "/api/health", "/api/email-verification/verify", "/api/email-verification/resend");
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) input;
        HttpServletResponse response = (HttpServletResponse) output;
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        String path = request.getServletPath();
        boolean mutation = !SAFE.contains(request.getMethod());
        if (mutation && !sameOrigin(request)) {
            ApiJson.error(response, 403, "Cross-origin requests are not permitted.");
            return;
        }
        if (!PUBLIC.contains(path)) {
            Integer id = SessionUtil.getAuthenticatedPersonnelId(request);
            if (id == null) { ApiJson.error(response, 401, "Authentication is required."); return; }
            try {
                if (!refreshSession(request.getSession(false), id)) {
                    request.getSession(false).invalidate();
                    ApiJson.error(response, 403, "This account no longer has active access.");
                    return;
                }
            } catch (SQLException ex) {
                LOG.log(Level.SEVERE, "Unable to validate the active account.", ex);
                ApiJson.error(response, 503, "Account validation is temporarily unavailable.");
                return;
            }
            if (mutation && !CsrfUtil.isRequestTokenValid(request)) {
                ApiJson.error(response, 403, "The security token is missing or invalid. Refresh and try again.");
                return;
            }
        }
        if (mutation) {
            String contentType = request.getContentType();
            boolean multipart = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data");
            boolean upload = path.equals("/api/profile/photo") || path.equals("/api/service-request-attachments");
            if (!(upload && multipart) && !path.equals("/api/logout") && !request.getMethod().equals("DELETE")) {
                if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                    ApiJson.error(response, 415, "Content-Type must be application/json."); return;
                }
                if (request.getContentLengthLong() > MAX_JSON_BYTES) {
                    ApiJson.error(response, 413, "The JSON request is too large."); return;
                }
                byte[] body = request.getInputStream().readNBytes(MAX_JSON_BYTES + 1);
                if (body.length > MAX_JSON_BYTES) {
                    ApiJson.error(response, 413, "The JSON request is too large."); return;
                }
                try {
                    if (!JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).isJsonObject())
                        throw new JsonParseException("Expected object");
                } catch (JsonParseException ex) {
                    ApiJson.error(response, 400, "A valid JSON object is required."); return;
                }
                request = new BufferedJsonRequest(request, body);
            }
        }
        chain.doFilter(request, response);
    }

    static boolean refreshSession(HttpSession session, int id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT sp.Account_Status,sp.Department_ID,pra.Role_ID FROM SCHOOL_PERSONNEL sp
                JOIN PERSONNEL_ROLE_ASSIGNMENT pra ON pra.Personnel_ID=sp.Personnel_ID
                WHERE sp.Personnel_ID=?
                """)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !"Active".equals(rs.getString(1)) || rs.getInt(3) < 1 || rs.getInt(3) > 4) return false;
                session.setAttribute("roleId", rs.getInt(3));
                session.setAttribute("departmentId", rs.getInt(2));
                return true;
            }
        }
    }

    static boolean sameOrigin(HttpServletRequest request) {
        if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) return false;
        String origin = request.getHeader("Origin");
        if (origin == null) return true; // Non-browser clients still require session + CSRF.
        String expected = request.getScheme() + "://" + request.getServerName();
        int port = request.getServerPort();
        if (!(port == 80 && "http".equals(request.getScheme())) && !(port == 443 && "https".equals(request.getScheme())))
            expected += ":" + port;
        return expected.equalsIgnoreCase(origin);
    }

    private static final class BufferedJsonRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedJsonRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream bytes = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return bytes.read(); }
                @Override public boolean isFinished() { return bytes.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new IllegalStateException("Asynchronous JSON reading is unsupported."); }
            };
        }
    }
}
