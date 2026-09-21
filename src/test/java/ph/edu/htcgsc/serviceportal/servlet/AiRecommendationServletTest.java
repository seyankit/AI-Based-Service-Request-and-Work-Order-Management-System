package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.dao.AiRecommendationDAO;
import ph.edu.htcgsc.serviceportal.service.AiRecommendationService;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRecommendationServletTest {

    @Test
    void unauthenticatedWrongRoleAndCsrfAreRejected() throws Exception {
        AiRecommendationServlet servlet = new AiRecommendationServlet(successAnalyzer());
        RecordingResponse unauthenticated = new RecordingResponse();
        servlet.doPost(request(null, null, null, "{\"requestId\":7}"), unauthenticated.proxy());
        assertEquals(401, unauthenticated.status);

        RecordingResponse wrongRole = new RecordingResponse();
        servlet.doPost(request(17, 1, "csrf", "{\"requestId\":7}"), wrongRole.proxy());
        assertEquals(403, wrongRole.status);

        RecordingResponse csrf = new RecordingResponse();
        servlet.doPost(request(17, 2, "session", "{\"requestId\":7}", "other"), csrf.proxy());
        assertEquals(403, csrf.status);
    }

    @Test
    void onlyPositiveRequestIdIsAccepted() throws Exception {
        AiRecommendationServlet servlet = new AiRecommendationServlet(successAnalyzer());
        for (String body : new String[] {"{}", "{\"requestId\":0}", "{\"requestId\":7,\"actorId\":99}", "{bad"}) {
            RecordingResponse response = new RecordingResponse();
            servlet.doPost(request(17, 2, "csrf", body), response.proxy());
            assertEquals(400, response.status);
        }
    }

    @Test
    void resultAndFailuresUseSafeResponseMappings() throws Exception {
        RecordingResponse success = new RecordingResponse();
        new AiRecommendationServlet(successAnalyzer()).doPost(request(17, 2, "csrf", "{\"requestId\":7}"), success.proxy());
        assertEquals(200, success.status);
        assertTrue(success.body().contains("\"available\":true"));

        RecordingResponse unavailable = new RecordingResponse();
        new AiRecommendationServlet((actor, role, requestId) -> unavailableResult()).doPost(request(17, 2, "csrf", "{\"requestId\":7}"), unavailable.proxy());
        assertEquals(200, unavailable.status);
        assertTrue(unavailable.body().contains("\"available\":false"));

        RecordingResponse missing = new RecordingResponse();
        new AiRecommendationServlet((actor, role, requestId) -> { throw new AiRecommendationDAO.RequestNotFoundException(); })
                .doPost(request(17, 2, "csrf", "{\"requestId\":7}"), missing.proxy());
        assertEquals(404, missing.status);

        RecordingResponse ineligible = new RecordingResponse();
        new AiRecommendationServlet((actor, role, requestId) -> { throw new AiRecommendationDAO.IneligibleRequestException(); })
                .doPost(request(17, 2, "csrf", "{\"requestId\":7}"), ineligible.proxy());
        assertEquals(409, ineligible.status);
    }

    private AiRecommendationService.Analyzer successAnalyzer() {
        return (actor, role, requestId) -> new AiRecommendationService.Result(true, "Completed", "Ready.", 1,
                "Internet & Network", BigDecimal.valueOf(.5), "Medium", BigDecimal.valueOf(.5), null,
                null, null, List.of(), "Category reason.", "Priority reason.", "No duplicate.", "Rule-Based", "Rules", "1");
    }

    private AiRecommendationService.Result unavailableResult() {
        return new AiRecommendationService.Result(false, "Unavailable", "Unavailable.", 0, null,
                null, null, null, null, null, null, List.of(), null, null, null, "None", null, null);
    }

    private HttpServletRequest request(Integer personnelId, Integer roleId, String csrf, String body) {
        return request(personnelId, roleId, csrf, body, csrf);
    }

    private HttpServletRequest request(Integer personnelId, Integer roleId, String sessionCsrf, String body, String headerCsrf) {
        Map<String, Object> attributes = new HashMap<>();
        if (personnelId != null) attributes.put("personnelId", personnelId);
        if (roleId != null) attributes.put("roleId", roleId);
        if (sessionCsrf != null) attributes.put("csrfToken", sessionCsrf);
        HttpSession session = (HttpSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {HttpSession.class},
                (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) return attributes.get(args[0]);
                    return defaultValue(method.getReturnType());
                });
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSession" -> session;
                    case "getContentType" -> "application/json";
                    case "getContentLengthLong" -> (long) body.length();
                    case "getReader" -> new BufferedReader(new StringReader(body));
                    case "getHeader" -> "X-CSRF-Token".equals(args[0]) ? headerCsrf : null;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return 0;
    }

    private static final class RecordingResponse {
        int status = 200;
        final StringWriter body = new StringWriter();
        HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {HttpServletResponse.class},
                    (proxy, method, args) -> {
                        if ("setStatus".equals(method.getName())) status = (int) args[0];
                        if ("getWriter".equals(method.getName())) return new PrintWriter(body, true);
                        return defaultValue(method.getReturnType());
                    });
        }
        String body() { return body.toString(); }
    }
}
