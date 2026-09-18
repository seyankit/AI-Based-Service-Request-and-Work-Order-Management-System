package ph.edu.htcgsc.serviceportal.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.EmailVerificationDAO;
import ph.edu.htcgsc.serviceportal.util.VerificationTokenUtil;

import java.io.IOException;

public class EmailVerificationServlet extends HttpServlet {

    private final EmailVerificationDAO dao = new EmailVerificationDAO();

    @Override
    protected void doGet(
            HttpServletRequest req,
            HttpServletResponse resp
    ) throws IOException {

        resp.setHeader("Cache-Control", "no-store");

        String token = req.getParameter("token");

        if (token == null || token.length() < 30) {
            redirectToVerificationResult(
                    req,
                    resp,
                    "invalid"
            );
            return;
        }

        try {
            var verification =
                    dao.findValid(
                            VerificationTokenUtil.sha256(token)
                    );

            if (verification == null) {
                redirectToVerificationResult(
                        req,
                        resp,
                        "invalid"
                );
                return;
            }

            String status =
                    dao.verifyAndActivate(verification);

            if ("Active".equalsIgnoreCase(status)) {
                redirectToVerificationResult(
                        req,
                        resp,
                        "success"
                );
            } else {
                redirectToVerificationResult(
                        req,
                        resp,
                        "pending"
                );
            }

        } catch (Exception e) {
            redirectToVerificationResult(
                    req,
                    resp,
                    "error"
            );
        }
    }

    private void redirectToVerificationResult(
            HttpServletRequest req,
            HttpServletResponse resp,
            String result
    ) throws IOException {

        String destination =
                req.getContextPath()
                        + "/verification-result.html?status="
                        + result;

        resp.sendRedirect(
                resp.encodeRedirectURL(destination)
        );
    }
}