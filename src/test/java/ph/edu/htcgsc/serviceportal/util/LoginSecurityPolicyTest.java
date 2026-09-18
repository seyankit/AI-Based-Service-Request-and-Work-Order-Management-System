package ph.edu.htcgsc.serviceportal.util;

import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.model.LoginFailureState;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LoginSecurityPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");

    private static LoginFailureState failures(int count, Instant lastFailureAt) {
        return new LoginFailureState(
                count,
                lastFailureAt == null ? null : lastFailureAt.minusSeconds(count),
                lastFailureAt,
                null
        );
    }

    @Test
    void policyUsesFiveAttemptsAndFifteenMinutes() {
        assertEquals(5, LoginSecurityPolicy.MAX_FAILED_ATTEMPTS);
        assertEquals(15, LoginSecurityPolicy.LOCKOUT_MINUTES);
    }

    @Test
    void firstFailureStartsCountingFromOne() {
        LoginFailureState next = LoginSecurityPolicy.afterFailedAttempt(null, NOW);

        assertEquals(1, next.failedAttemptCount());
        assertEquals(NOW, next.failureWindowStartedAt());
        assertEquals(NOW, next.lastFailedAttemptAt());
        assertNull(next.lockedUntil());
    }

    @Test
    void failuresBelowThresholdNeverLock() {
        LoginFailureState state = failures(1, NOW.minusSeconds(60));
        for (int expected = 2; expected < LoginSecurityPolicy.MAX_FAILED_ATTEMPTS; expected++) {
            state = LoginSecurityPolicy.afterFailedAttempt(state, NOW);
            assertEquals(expected, state.failedAttemptCount());
            assertNull(state.lockedUntil());
        }
    }

    @Test
    void fifthFailureLocksAccountForFifteenMinutes() {
        LoginFailureState fourth = failures(4, NOW.minusSeconds(30));

        LoginFailureState fifth = LoginSecurityPolicy.afterFailedAttempt(fourth, NOW);

        assertEquals(5, fifth.failedAttemptCount());
        assertEquals(NOW.plusSeconds(15 * 60), fifth.lockedUntil());
        assertTrue(LoginSecurityPolicy.isLocked(fifth, NOW));
    }

    @Test
    void activeLockStaysUntouchedWhileLocked() {
        LoginFailureState locked = new LoginFailureState(
                5, NOW.minusSeconds(60), NOW.minusSeconds(60), NOW.plusSeconds(15 * 60)
        );

        LoginFailureState next = LoginSecurityPolicy.afterFailedAttempt(locked, NOW);

        assertEquals(locked, next);
        assertTrue(LoginSecurityPolicy.isLocked(next, NOW));
    }

    @Test
    void correctPasswordCheckIsIndependentOfLockState() {
        // The servlet checks the lock before verifying the password, so a
        // locked account is blocked regardless of password correctness.
        // The policy must therefore report the lock purely from state.
        LoginFailureState locked = new LoginFailureState(5, NOW, NOW, NOW.plusSeconds(60));
        assertTrue(LoginSecurityPolicy.isLocked(locked, NOW));
        assertTrue(LoginSecurityPolicy.remainingLockSeconds(locked, NOW) > 0);
    }

    @Test
    void expiredLockStartsFreshWindowWithoutLock() {
        LoginFailureState expired = new LoginFailureState(
                5, NOW.minusSeconds(16 * 60), NOW.minusSeconds(16 * 60), NOW.minusSeconds(1)
        );

        LoginFailureState next = LoginSecurityPolicy.afterFailedAttempt(expired, NOW);

        assertEquals(1, next.failedAttemptCount());
        assertEquals(NOW, next.failureWindowStartedAt());
        assertEquals(NOW, next.lastFailedAttemptAt());
        assertNull(next.lockedUntil());
        assertFalse(LoginSecurityPolicy.isLocked(next, NOW));
    }

    @Test
    void staleFailuresOlderThanLockoutPeriodStartFreshWindow() {
        LoginFailureState stale = failures(4, NOW.minusSeconds(16 * 60));

        LoginFailureState next = LoginSecurityPolicy.afterFailedAttempt(stale, NOW);

        assertEquals(1, next.failedAttemptCount());
        assertNull(next.lockedUntil());
    }

    @Test
    void recentFailuresKeepCountingWithinTheWindow() {
        LoginFailureState recent = failures(3, NOW.minusSeconds(14 * 60));

        LoginFailureState next = LoginSecurityPolicy.afterFailedAttempt(recent, NOW);

        assertEquals(4, next.failedAttemptCount());
        assertEquals(recent.failureWindowStartedAt(), next.failureWindowStartedAt());
        assertNull(next.lockedUntil());
    }

    @Test
    void lockExpiryBoundaryIsExclusive() {
        Instant until = NOW.plusSeconds(15 * 60);

        assertFalse(LoginSecurityPolicy.isLocked(until, until));
        assertTrue(LoginSecurityPolicy.isLocked(until, until.minusSeconds(1)));
        assertFalse(LoginSecurityPolicy.isLocked((Instant) null, NOW));
    }

    @Test
    void remainingLockSecondsNeverNegative() {
        Instant until = NOW.plusSeconds(15 * 60);
        assertEquals(15 * 60, LoginSecurityPolicy.remainingLockSeconds(until, NOW));
        assertEquals(0, LoginSecurityPolicy.remainingLockSeconds(NOW.minusSeconds(1), NOW));
        assertEquals(0, LoginSecurityPolicy.remainingLockSeconds((Instant) null, NOW));
        assertEquals(0, LoginSecurityPolicy.remainingLockSeconds((LoginFailureState) null, NOW));
    }
}
