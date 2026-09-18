package ph.edu.htcgsc.serviceportal.util;

import ph.edu.htcgsc.serviceportal.model.LoginFailureState;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure decision logic for the temporary login lockout introduced with
 * migration 014 (ACCOUNT_LOGIN_SECURITY).
 *
 * Policy:
 * - Five failed attempts within the active failure window lock the account.
 * - The lockout lasts fifteen minutes and blocks every password,
 *   including the correct one, until it expires.
 * - Failed attempts older than one lockout period stop counting, so old
 *   failures never accumulate toward a future lockout.
 * - A successful login clears the failure state (handled by the DAO).
 */
public final class LoginSecurityPolicy {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int LOCKOUT_MINUTES = 15;

    private static final Duration FAILURE_WINDOW =
            Duration.ofMinutes(LOCKOUT_MINUTES);

    private LoginSecurityPolicy() {
    }

    /**
     * @return true while the account must be treated as locked at the
     *         given instant. A null state is never locked.
     */
    public static boolean isLocked(LoginFailureState state, Instant now) {
        return state != null && isLocked(state.lockedUntil(), now);
    }

    public static boolean isLocked(Instant lockedUntil, Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * @return whole seconds until the lock expires, never negative.
     */
    public static long remainingLockSeconds(LoginFailureState state, Instant now) {
        if (state == null) {
            return 0;
        }
        return remainingLockSeconds(state.lockedUntil(), now);
    }

    public static long remainingLockSeconds(Instant lockedUntil, Instant now) {
        if (lockedUntil == null) {
            return 0;
        }
        return Math.max(0, Duration.between(now, lockedUntil).getSeconds());
    }

    /**
     * @return the instant at which a lock started at {@code now} expires.
     */
    public static Instant lockExpiry(Instant now) {
        return now.plus(FAILURE_WINDOW);
    }

    /**
     * Compute the next security state after one more failed attempt.
     *
     * @param current the stored state, or null when the account has no
     *                ACCOUNT_LOGIN_SECURITY row yet
     * @param now     the instant of the failed attempt
     * @return the state to persist; its {@code lockedUntil} is non-null
     *         exactly when this failure triggered a new lockout
     */
    public static LoginFailureState afterFailedAttempt(
            LoginFailureState current,
            Instant now
    ) {
        if (isLocked(current, now)) {
            // Already locked: keep the active lock untouched.
            return current;
        }

        if (isStaleWindow(current, now)) {
            return freshWindow(now);
        }

        int nextCount = current.failedAttemptCount() + 1;

        if (nextCount >= MAX_FAILED_ATTEMPTS) {
            return new LoginFailureState(
                    nextCount,
                    current.failureWindowStartedAt(),
                    now,
                    lockExpiry(now)
            );
        }

        return new LoginFailureState(
                nextCount,
                current.failureWindowStartedAt(),
                now,
                null
        );
    }

    /**
     * A failure window is stale when there is nothing current to build on:
     * no record at all, no recorded failure, a lock that already expired,
     * or a last failure older than one lockout period.
     */
    private static boolean isStaleWindow(
            LoginFailureState current,
            Instant now
    ) {
        if (current == null || current.lastFailedAttemptAt() == null) {
            return true;
        }

        if (current.lockedUntil() != null) {
            // An expired lock always starts a clean window; this also keeps
            // the stored row valid for CHECK constraints (Locked_Until must
            // be NULL unless at least five failures are recorded).
            return true;
        }

        return Duration.between(current.lastFailedAttemptAt(), now)
                .compareTo(FAILURE_WINDOW) >= 0;
    }

    private static LoginFailureState freshWindow(Instant now) {
        return new LoginFailureState(1, now, now, null);
    }
}
