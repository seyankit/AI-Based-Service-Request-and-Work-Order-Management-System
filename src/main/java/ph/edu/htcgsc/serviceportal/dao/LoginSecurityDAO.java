package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.model.LoginFailureState;
import ph.edu.htcgsc.serviceportal.util.LoginSecurityPolicy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Tracks failed login attempts and temporary lockouts in the
 * ACCOUNT_LOGIN_SECURITY table created by migration 014.
 *
 * One row per personnel account is created on demand; rows are never
 * deleted. All statements are prepared statements.
 */
public class LoginSecurityDAO {

    /**
     * Read the stored security state, or null when the account has no row.
     */
    public LoginFailureState findState(int personnelId) throws SQLException {
        String sql = """
                SELECT Failed_Attempt_Count, Failure_Window_Started_At,
                       Last_Failed_Attempt_At, Locked_Until
                FROM ACCOUNT_LOGIN_SECURITY
                WHERE Personnel_ID = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, personnelId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? map(resultSet) : null;
            }
        }
    }

    /**
     * Record one failed attempt and return the resulting state. The
     * read-modify-write runs in a transaction with a row lock so concurrent
     * attempts cannot lose counts. Creates the row when it does not exist.
     */
    public LoginFailureState recordFailedAttempt(int personnelId, Instant now)
            throws SQLException {

        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                LoginFailureState current = selectForUpdate(connection, personnelId);
                LoginFailureState next = LoginSecurityPolicy.afterFailedAttempt(current, now);

                if (current == null) {
                    insert(connection, personnelId, next);
                } else {
                    update(connection, personnelId, next);
                }

                connection.commit();
                return next;

            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException(exception);

            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /**
     * Clear the failure state after a successful login and stamp the
     * successful login time. Upserts so it works whether or not a failure
     * row exists, and always leaves the row consistent with the CHECK
     * constraints (zero failures, all failure timestamps NULL).
     */
    public void resetOnSuccess(int personnelId, Instant now) throws SQLException {
        String sql = """
                INSERT INTO ACCOUNT_LOGIN_SECURITY
                    (Personnel_ID, Failed_Attempt_Count, Failure_Window_Started_At,
                     Last_Failed_Attempt_At, Locked_Until, Last_Successful_Login_At)
                VALUES (?, 0, NULL, NULL, NULL, ?)
                ON DUPLICATE KEY UPDATE
                    Failed_Attempt_Count = 0,
                    Failure_Window_Started_At = NULL,
                    Last_Failed_Attempt_At = NULL,
                    Locked_Until = NULL,
                    Last_Successful_Login_At = VALUES(Last_Successful_Login_At)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, personnelId);
            statement.setTimestamp(2, timestamp(now));
            statement.executeUpdate();
        }
    }

    private LoginFailureState selectForUpdate(Connection connection, int personnelId)
            throws SQLException {

        String sql = """
                SELECT Failed_Attempt_Count, Failure_Window_Started_At,
                       Last_Failed_Attempt_At, Locked_Until
                FROM ACCOUNT_LOGIN_SECURITY
                WHERE Personnel_ID = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, personnelId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? map(resultSet) : null;
            }
        }
    }

    private void insert(Connection connection, int personnelId, LoginFailureState next)
            throws SQLException {

        String sql = """
                INSERT INTO ACCOUNT_LOGIN_SECURITY
                    (Personnel_ID, Failed_Attempt_Count, Failure_Window_Started_At,
                     Last_Failed_Attempt_At, Locked_Until)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, personnelId);
            statement.setInt(2, next.failedAttemptCount());
            statement.setTimestamp(3, timestamp(next.failureWindowStartedAt()));
            statement.setTimestamp(4, timestamp(next.lastFailedAttemptAt()));
            statement.setTimestamp(5, timestamp(next.lockedUntil()));
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, int personnelId, LoginFailureState next)
            throws SQLException {

        String sql = """
                UPDATE ACCOUNT_LOGIN_SECURITY
                SET Failed_Attempt_Count = ?,
                    Failure_Window_Started_At = ?,
                    Last_Failed_Attempt_At = ?,
                    Locked_Until = ?
                WHERE Personnel_ID = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, next.failedAttemptCount());
            statement.setTimestamp(2, timestamp(next.failureWindowStartedAt()));
            statement.setTimestamp(3, timestamp(next.lastFailedAttemptAt()));
            statement.setTimestamp(4, timestamp(next.lockedUntil()));
            statement.setInt(5, personnelId);
            statement.executeUpdate();
        }
    }

    private LoginFailureState map(ResultSet resultSet) throws SQLException {
        return new LoginFailureState(
                resultSet.getInt("Failed_Attempt_Count"),
                instant(resultSet.getTimestamp("Failure_Window_Started_At")),
                instant(resultSet.getTimestamp("Last_Failed_Attempt_At")),
                instant(resultSet.getTimestamp("Locked_Until"))
        );
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
