package ph.edu.htcgsc.serviceportal.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class CsrfUtil {

    public static final String HEADER_NAME =
            "X-CSRF-Token";

    private static final String SESSION_ATTRIBUTE =
            "csrfToken";

    private static final int TOKEN_BYTE_LENGTH = 32;

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private CsrfUtil() {
    }

    /*
     * Returns a token only when an existing authenticated
     * session is available. It never creates a new session.
     */
    public static String getOrCreateToken(
            HttpServletRequest request
    ) {
        HttpSession session =
                SessionUtil.getExistingSession(request);

        if (session == null) {
            return null;
        }

        synchronized (session) {
            Object existingToken =
                    session.getAttribute(
                            SESSION_ATTRIBUTE
                    );

            if (
                existingToken instanceof String token
                && !token.isBlank()
            ) {
                return token;
            }

            byte[] randomBytes =
                    new byte[TOKEN_BYTE_LENGTH];

            SECURE_RANDOM.nextBytes(randomBytes);

            String newToken =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(randomBytes);

            session.setAttribute(
                    SESSION_ATTRIBUTE,
                    newToken
            );

            return newToken;
        }
    }

    public static boolean isRequestTokenValid(
            HttpServletRequest request
    ) {
        HttpSession session =
                SessionUtil.getExistingSession(request);

        if (session == null) {
            return false;
        }

        Object storedValue =
                session.getAttribute(
                        SESSION_ATTRIBUTE
                );

        if (!(storedValue instanceof String storedToken)) {
            return false;
        }

        String submittedToken =
                request.getHeader(HEADER_NAME);

        if (
            submittedToken == null
            || submittedToken.isBlank()
        ) {
            return false;
        }

        byte[] storedBytes =
                storedToken.getBytes(
                        StandardCharsets.UTF_8
                );

        byte[] submittedBytes =
                submittedToken.trim().getBytes(
                        StandardCharsets.UTF_8
                );

        return MessageDigest.isEqual(
                storedBytes,
                submittedBytes
        );
    }

    public static void removeToken(
            HttpSession session
    ) {
        if (session != null) {
            session.removeAttribute(
                    SESSION_ATTRIBUTE
            );
        }
    }
}