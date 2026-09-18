package ph.edu.htcgsc.serviceportal.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilDummyVerificationTest {

    @Test
    void dummyVerificationAlwaysFails() {
        assertFalse(PasswordUtil.verifyDummyPassword("Strong#123"));
        assertFalse(PasswordUtil.verifyDummyPassword("another-password"));
    }

    @Test
    void dummyVerificationHandlesNullSafely() {
        assertFalse(PasswordUtil.verifyDummyPassword(null));
    }

    @Test
    void dummyVerificationPerformsFullPbkdf2Work() {
        long startNanos = System.nanoTime();
        PasswordUtil.verifyDummyPassword("Strong#123");
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertTrue(
                elapsedMillis >= 20,
                "Dummy verification should run PBKDF2 like a real check, took "
                        + elapsedMillis + " ms"
        );
    }

    @Test
    void realPasswordRoundTripIsPreserved() {
        String stored = PasswordUtil.hashPassword("Strong#123");

        assertTrue(PasswordUtil.verifyPassword("Strong#123", stored));
        assertFalse(PasswordUtil.verifyPassword("Other#456", stored));
    }

    @Test
    void realVerificationRunsComparableWorkToDummyVerification() {
        long realStart = System.nanoTime();
        assertTrue(PasswordUtil.verifyPassword(
                "Strong#123",
                PasswordUtil.hashPassword("Strong#123")
        ));
        long realMillis = (System.nanoTime() - realStart) / 1_000_000;

        long dummyStart = System.nanoTime();
        PasswordUtil.verifyDummyPassword("Strong#123");
        long dummyMillis = (System.nanoTime() - dummyStart) / 1_000_000;

        assertTrue(
                realMillis >= 20 && dummyMillis >= 20,
                "Both checks should run PBKDF2 work, real=" + realMillis
                        + " ms, dummy=" + dummyMillis + " ms"
        );
    }
}
