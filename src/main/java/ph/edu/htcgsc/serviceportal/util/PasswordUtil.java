package ph.edu.htcgsc.serviceportal.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int MIN_ACCEPTED_ITERATIONS = 100_000;
    private static final int MAX_ACCEPTED_ITERATIONS = 1_000_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

    private PasswordUtil() {
    }

    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty.");
        }

        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);

        byte[] hash = generateHash(password.toCharArray(), salt, ITERATIONS);

        return ITERATIONS
                + ":"
                + Base64.getEncoder().encodeToString(salt)
                + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verifyPassword(String password, String storedPassword) {
        if (password == null || storedPassword == null) {
            return false;
        }

        String[] parts = storedPassword.split(":", -1);
        if (parts.length != 3) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[0]);
            if (iterations < MIN_ACCEPTED_ITERATIONS
                    || iterations > MAX_ACCEPTED_ITERATIONS) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[2]);

            if (salt.length < SALT_LENGTH || expectedHash.length != KEY_LENGTH / 8) {
                return false;
            }

            byte[] actualHash = generateHash(
                    password.toCharArray(),
                    salt,
                    iterations
            );

            return MessageDigest.isEqual(expectedHash, actualHash);

        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Timing-normalization check used when a sign-in attempt names an email
     * address that does not exist. It runs a full PBKDF2 verification
     * against a fixed dummy hash so the response timing is similar to an
     * attempt against a real account, and it always returns {@code false}.
     */
    public static boolean verifyDummyPassword(String password) {
        return verifyPassword(password, DummyHashHolder.DUMMY_STORED_HASH);
    }

    /**
     * Builds the constant dummy hash lazily on first use, with a fixed salt,
     * so the cost and format always match the current PBKDF2 settings.
     */
    private static final class DummyHashHolder {

        private static final String DUMMY_PASSWORD =
                "htc-service-portal-timing-normalization";

        private static final byte[] DUMMY_SALT = {
            (byte) 0x7a, (byte) 0x3c, (byte) 0x11, (byte) 0xf2,
            (byte) 0x9e, (byte) 0x40, (byte) 0x6b, (byte) 0xd5,
            (byte) 0x28, (byte) 0xc7, (byte) 0x83, (byte) 0x1a,
            (byte) 0xf6, (byte) 0x59, (byte) 0xae, (byte) 0x0d
        };

        private static final String DUMMY_STORED_HASH = build();

        private static String build() {
            byte[] hash = generateHash(
                    DUMMY_PASSWORD.toCharArray(),
                    DUMMY_SALT,
                    ITERATIONS
            );

            return ITERATIONS
                    + ":"
                    + Base64.getEncoder().encodeToString(DUMMY_SALT)
                    + ":"
                    + Base64.getEncoder().encodeToString(hash);
        }
    }

    private static byte[] generateHash(
            char[] password,
            byte[] salt,
            int iterations) {

        PBEKeySpec specification = new PBEKeySpec(
                password,
                salt,
                iterations,
                KEY_LENGTH
        );

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(specification).getEncoded();

        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Unable to hash password.", exception);

        } finally {
            specification.clearPassword();
        }
    }
}
