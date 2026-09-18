package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.model.RequestStatusHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class RequestStatusHistoryDAO {

    public static final class LookupResult {

        private final boolean requestFound;
        private final List<RequestStatusHistory> history;

        private LookupResult(
                boolean requestFound,
                List<RequestStatusHistory> history
        ) {
            this.requestFound = requestFound;
            this.history = List.copyOf(history);
        }

        public boolean isRequestFound() {
            return requestFound;
        }

        public List<RequestStatusHistory> getHistory() {
            return history;
        }
    }

    /*
     * The ownership condition is enforced inside the SQL query.
     * A requester cannot retrieve another requester's history.
     */
    public LookupResult findForRequester(
            long requestId,
            int requesterId
    ) throws SQLException {

        if (requestId <= 0) {
            throw new IllegalArgumentException(
                    "The request ID must be positive."
            );
        }

        if (requesterId <= 0) {
            throw new IllegalArgumentException(
                    "The requester ID must be positive."
            );
        }

        String sql = """
                SELECT
                    sr.Request_ID AS Owned_Request_ID,
                    rsh.History_ID,
                    rsh.Previous_Status,
                    rsh.New_Status,
                    rsh.Changed_By,
                    rsh.Changed_By_Role_ID,
                    rsh.Change_Reason,
                    rsh.Changed_At
                FROM SERVICE_REQUEST sr
                LEFT JOIN REQUEST_STATUS_HISTORY rsh
                    ON rsh.Request_ID = sr.Request_ID
                WHERE sr.Request_ID = ?
                  AND sr.Requester_ID = ?
                ORDER BY
                    rsh.Changed_At ASC,
                    rsh.History_ID ASC
                """;

        List<RequestStatusHistory> history =
                new ArrayList<>();

        boolean requestFound = false;

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    requestId
            );

            statement.setInt(
                    2,
                    requesterId
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                while (result.next()) {
                    requestFound = true;

                    long historyId =
                            result.getLong("History_ID");

                    if (result.wasNull()) {
                        continue;
                    }

                    RequestStatusHistory entry =
                            new RequestStatusHistory();

                    entry.setHistoryId(
                            historyId
                    );

                    entry.setRequestId(
                            result.getLong(
                                    "Owned_Request_ID"
                            )
                    );

                    entry.setPreviousStatus(
                            result.getString(
                                    "Previous_Status"
                            )
                    );

                    entry.setNewStatus(
                            result.getString(
                                    "New_Status"
                            )
                    );

                    entry.setChangedBy(
                            result.getInt(
                                    "Changed_By"
                            )
                    );

                    entry.setChangedByRoleId(
                            result.getInt(
                                    "Changed_By_Role_ID"
                            )
                    );

                    entry.setChangeReason(
                            result.getString(
                                    "Change_Reason"
                            )
                    );

                    entry.setChangedAt(
                            toIsoTimestamp(
                                    result.getTimestamp(
                                            "Changed_At"
                                    )
                            )
                    );

                    history.add(entry);
                }
            }
        }

        return new LookupResult(
                requestFound,
                history
        );
    }

    private String toIsoTimestamp(
            Timestamp value
    ) {
        return value == null
                ? null
                : value.toLocalDateTime().toString();
    }
}