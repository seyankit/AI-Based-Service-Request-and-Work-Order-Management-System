package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import jakarta.servlet.http.*;
import ph.edu.htcgsc.serviceportal.dao.*;
import ph.edu.htcgsc.serviceportal.model.Personnel;
import ph.edu.htcgsc.serviceportal.util.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public class ResendVerificationServlet extends HttpServlet {
    private static class Body{String email;}
    private final Gson gson=new Gson(); private final PersonnelDAO personnelDAO=new PersonnelDAO(); private final EmailVerificationDAO dao=new EmailVerificationDAO(); private final MailService mail=new MailService();
    protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        prep(resp); try{Body b=gson.fromJson(req.getReader(),Body.class);String email=RegistrationValidator.normalizeEmail(b==null?null:b.email);Personnel p=personnelDAO.findByEmail(email);
            if(p==null||!"Pending Email Verification".equalsIgnoreCase(p.getAccountStatus())){generic(resp);return;}
            if(!dao.canResend(p.getPersonnelId())){resp.setStatus(429);resp.getWriter().write(gson.toJson(Map.of("success",false,"message","Please wait at least 60 seconds before requesting another verification email.")));return;}
            String token=VerificationTokenUtil.newToken();dao.replaceToken(p.getPersonnelId(),VerificationTokenUtil.sha256(token),Instant.now().plusSeconds(3600));mail.sendVerification(p.getEmail(),p.getFirstName(),token);generic(resp);
        }catch(Exception e){resp.setStatus(500);resp.getWriter().write(gson.toJson(Map.of("success",false,"message","Unable to resend verification email.")));}
    }
    private void generic(HttpServletResponse r)throws IOException{r.setStatus(200);r.getWriter().write(gson.toJson(Map.of("success",true,"message","If the account is awaiting verification, a new verification email has been sent.")));}
    private void prep(HttpServletResponse r){r.setContentType("application/json");r.setCharacterEncoding("UTF-8");r.setHeader("Cache-Control","no-store");}
}
