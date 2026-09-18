package ph.edu.htcgsc.serviceportal.util;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

public class MailService {
    public void sendVerification(String email,String firstName,String token) throws MessagingException {
        String host=require("HTC_MAIL_HOST"), user=require("HTC_MAIL_USER"), pass=require("HTC_MAIL_PASSWORD");
        String from=env("HTC_MAIL_FROM",user);
        String base=require("HTC_APP_BASE_URL"); int port=Integer.parseInt(env("HTC_MAIL_PORT","587"));
        Properties props=new Properties(); props.put("mail.smtp.auth","true"); props.put("mail.smtp.starttls.enable","true"); props.put("mail.smtp.host",host); props.put("mail.smtp.port",String.valueOf(port));
        Session session=Session.getInstance(props,new Authenticator(){ protected PasswordAuthentication getPasswordAuthentication(){ return new PasswordAuthentication(user,pass);} });
        Message message=new MimeMessage(session); message.setFrom(new InternetAddress(from)); message.setRecipients(Message.RecipientType.TO,InternetAddress.parse(email,false));
        message.setSubject("Verify Your Holy Trinity College Service Portal Account");
        String link=base.replaceAll("/$","")+"/api/email-verification/verify?token="+java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
        message.setText("Hello "+firstName+",\n\nThank you for registering for the Holy Trinity College Service Portal.\n\nVerify your school email address using this link:\n"+link+"\n\nThis link expires in 60 minutes and can be used only once.\n\nIf you did not create this account, you may ignore this message.\n\nHoly Trinity College Service Portal\nAI-Based Service Request and Work Order Management System");
        Transport.send(message);
    }
    private static String env(String n,String d){ String v=System.getenv(n); return v==null||v.isBlank()?d:v.trim(); }
    private static String require(String n){ String v=System.getenv(n); if(v==null||v.isBlank()) throw new IllegalStateException(n+" is not configured."); return v.trim(); }
}
