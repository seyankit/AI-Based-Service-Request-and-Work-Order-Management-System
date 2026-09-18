package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.*;
import jakarta.servlet.http.*;
import ph.edu.htcgsc.serviceportal.dao.*;
import ph.edu.htcgsc.serviceportal.model.Personnel;
import ph.edu.htcgsc.serviceportal.util.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.logging.*;

public class RegisterServlet extends HttpServlet {
    private static final Logger LOGGER=Logger.getLogger(RegisterServlet.class.getName());
    private final Gson gson=new Gson(); private final PersonnelDAO personnelDAO=new PersonnelDAO(); private final EmailVerificationDAO verificationDAO=new EmailVerificationDAO(); private final MailService mailService=new MailService();
    private static class RegisterRequest { String firstName,middleName,lastName,suffix,email,contactNumber,personnelType,password; int departmentId; JsonElement roleId,requestedRole,systemRole; }
    protected void doPost(HttpServletRequest request,HttpServletResponse response)throws IOException{
        prepare(response); if(request.getContentType()==null||!request.getContentType().toLowerCase(Locale.ROOT).startsWith("application/json")){ error(response,415,"Content-Type must be application/json."); return; }
        try{
            RegisterRequest d=gson.fromJson(request.getReader(),RegisterRequest.class); if(d==null){error(response,400,"Invalid registration data.");return;}
            if(d.roleId!=null||d.requestedRole!=null||d.systemRole!=null){ error(response,400,"System roles cannot be selected during registration."); return; }
            String first=RegistrationValidator.clean(d.firstName), middle=RegistrationValidator.clean(d.middleName), last=RegistrationValidator.clean(d.lastName), suffix=RegistrationValidator.clean(d.suffix), email=RegistrationValidator.normalizeEmail(d.email), contact=RegistrationValidator.normalizeContact(d.contactNumber), type=RegistrationValidator.clean(d.personnelType);
            if(!RegistrationValidator.validRequiredName(first)||!RegistrationValidator.validRequiredName(last)||!RegistrationValidator.validOptionalName(middle)||!RegistrationValidator.validSuffix(suffix)){error(response,400,"Enter a valid name. First and last name are required.");return;}
            if(!RegistrationValidator.validEmail(email)){error(response,400,"Please use a valid HTC school email ending in @online.htcgsc.edu.ph.");return;}
            if(!RegistrationValidator.validContact(contact)){error(response,400,"Contact number must use 09XXXXXXXXX or +639XXXXXXXXX format.");return;}
            if(!RegistrationValidator.validPersonnelType(type)){error(response,400,"Select a valid school personnel type.");return;}
            if(d.departmentId<=0||!personnelDAO.departmentExists(d.departmentId)){error(response,400,"Select a valid department or program.");return;}
            if(!RegistrationValidator.strongPassword(d.password)){error(response,400,"Password must be 8 to 72 characters and contain uppercase, lowercase, number, and special character.");return;}
            if(personnelDAO.emailExists(email)){error(response,409,"This school email is already registered.");return;}
            Personnel p=new Personnel(); p.setFirstName(first);p.setMiddleName(middle.isEmpty()?null:middle);p.setLastName(last);p.setSuffix(suffix.isEmpty()?null:suffix);p.setEmail(email);p.setContactNumber(contact);p.setPersonnelType(type);p.setDepartmentId(d.departmentId);p.setPasswordHash(PasswordUtil.hashPassword(d.password));p.setAccountStatus("Pending Email Verification");
            int id=personnelDAO.createPendingVerificationPersonnel(p); String token=VerificationTokenUtil.newToken(); verificationDAO.replaceToken(id,VerificationTokenUtil.sha256(token),Instant.now().plusSeconds(3600));
            boolean mailSent=true; String mailError=null; try{mailService.sendVerification(email,first,token);}catch(Exception ex){mailSent=false;mailError="Email delivery is not configured or failed. Use the resend endpoint after configuring mail.";LOGGER.log(Level.WARNING,"Verification email delivery failed for personnel "+id,ex);}
            Map<String,Object> out=new LinkedHashMap<>();out.put("success",true);out.put("verificationRequired",true);out.put("mailSent",mailSent);out.put("message",mailSent?"Registration successful. Check your school email to verify your account.":"Account created, but the verification email could not be sent. Configure mail and resend verification."); if(mailError!=null)out.put("mailNotice",mailError); out.put("maskedEmail",mask(email));
            response.setStatus(201);response.getWriter().write(gson.toJson(out));
        }catch(JsonParseException e){error(response,400,"Malformed JSON request.");}catch(Exception e){LOGGER.log(Level.SEVERE,"Registration failed",e);error(response,500,"Unable to create account.");}
    }
    private String mask(String e){int at=e.indexOf('@');if(at<=2)return e;return e.substring(0,2)+"****"+e.substring(at);}
    private void prepare(HttpServletResponse r){r.setContentType("application/json");r.setCharacterEncoding("UTF-8");r.setHeader("Cache-Control","no-store");r.setHeader("X-Content-Type-Options","nosniff");}
    private void error(HttpServletResponse r,int s,String m)throws IOException{r.setStatus(s);r.getWriter().write(gson.toJson(Map.of("success",false,"message",m)));}
}
