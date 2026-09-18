package ph.edu.htcgsc.serviceportal.model;

import java.time.Instant;

/**
 * Immutable snapshot of one account's row in ACCOUNT_LOGIN_SECURITY
 * (migration 014: account login security and temporary lockout).
 *
 * A null value means the corresponding column is NULL. Accounts without
 * a security row are represented by null (never stored as a record).
 */
public record LoginFailureState(
        int failedAttemptCount,
        Instant failureWindowStartedAt,
        Instant lastFailedAttemptAt,
        Instant lockedUntil
) {
}
