package ph.edu.htcgsc.serviceportal.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class VerificationTokenUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private VerificationTokenUtil() {}
    public static String newToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
