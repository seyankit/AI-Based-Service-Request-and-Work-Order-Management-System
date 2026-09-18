package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.model.ServiceRequest;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ServiceRequestQueryDAO {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAXIMUM_LIMIT = 100;

    /*
     * Returns only requests belonging to the authenticated requester.
     *
     * The requester ID must come from the server-side session,
     * never from a query parameter supplied by the browser.
     */
    public List<ServiceRequest> findByRequesterId(
            int requesterId,
            int requestedLimit
    ) throws SQLException {

        if (requesterId <= 0) {
            throw new IllegalArgumentException(
                    "The requester ID must be positive."
            );
        }

        int limit = normalizeLimit(requestedLimit);

        String sql = """
                SELECT
                    sr.Request_ID,
                    sr.Request_Number,
                    sr.Requester_ID,
                    sr.Requester_Department_ID,
                    d.Department_Name AS Requester_Department_Name,

                    sr.Requested_Category_ID,
                    requested_category.Category_Name
                        AS Requested_Category_Name,

                    sr.Final_Category_ID,
                    final_category.Category_Name
                        AS Final_Category_Name,

                    sr.Routed_Department_ID,
                    sr.Preferred_Priority,
                    sr.Final_Priority,
                    sr.Request_Title,
                    sr.Request_Description,
                    sr.Request_Location,
                    sr.Date_Reported,
                    sr.Current_Status,
                    sr.Completed_At,
                    sr.Closed_At,
                    sr.Created_At,
                    sr.Updated_At
                FROM SERVICE_REQUEST sr

                LEFT JOIN DEPARTMENT d
                    ON d.Department_ID =
                       sr.Requester_Department_ID

                LEFT JOIN SERVICE_CATEGORY requested_category
                    ON requested_category.Category_ID =
                       sr.Requested_Category_ID

                LEFT JOIN SERVICE_CATEGORY final_category
                    ON final_category.Category_ID =
                       sr.Final_Category_ID

                WHERE sr.Requester_ID = ?

                ORDER BY
                    sr.Created_At DESC,
                    sr.Request_ID DESC

                LIMIT ?
                """;

        List<ServiceRequest> requests =
                new ArrayList<>();

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    requesterId
            );

            statement.setInt(
                    2,
                    limit
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                while (result.next()) {
                    requests.add(
                            mapServiceRequest(result)
                    );
                }
            }
        }

        return requests;
    }

    private int normalizeLimit(
            int requestedLimit
    ) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(
                requestedLimit,
                MAXIMUM_LIMIT
        );
    }

    private ServiceRequest mapServiceRequest(
            ResultSet result
    ) throws SQLException {

        ServiceRequest request =
                new ServiceRequest();

        request.setRequestId(
                result.getLong("Request_ID")
        );

        request.setRequestNumber(
                result.getString("Request_Number")
        );

        request.setRequesterId(
                result.getInt("Requester_ID")
        );

        request.setRequesterDepartmentId(
                result.getInt(
                        "Requester_Department_ID"
                )
        );

        request.setRequesterDepartmentName(
                result.getString(
                        "Requester_Department_Name"
                )
        );
        request.setRequestedCategoryId(
                getNullableInteger(
                        result,
                        "Requested_Category_ID"
                )
        );

        request.setRequestedCategoryName(
                result.getString(
                        "Requested_Category_Name"
                )
        );
        request.setFinalCategoryId(
                getNullableInteger(
                        result,
                        "Final_Category_ID"
                )
        );

        request.setFinalCategoryName(
                result.getString(
                        "Final_Category_Name"
                )
        );
        request.setRoutedDepartmentId(
                getNullableInteger(
                        result,
                        "Routed_Department_ID"
                )
        );

        request.setPreferredPriority(
                result.getString(
                        "Preferred_Priority"
                )
        );

        request.setFinalPriority(
                result.getString(
                        "Final_Priority"
                )
        );

        request.setTitle(
                result.getString("Request_Title")
        );

        request.setDescription(
                result.getString(
                        "Request_Description"
                )
        );

        request.setLocation(
                result.getString(
                        "Request_Location"
                )
        );

        request.setDateReported(
                toIsoDate(
                        result.getDate(
                                "Date_Reported"
                        )
                )
        );

        request.setCurrentStatus(
                result.getString(
                        "Current_Status"
                )
        );

        request.setCompletedAt(
                toIsoTimestamp(
                        result.getTimestamp(
                                "Completed_At"
                        )
                )
        );

        request.setClosedAt(
                toIsoTimestamp(
                        result.getTimestamp(
                                "Closed_At"
                        )
                )
        );

        request.setCreatedAt(
                toIsoTimestamp(
                        result.getTimestamp(
                                "Created_At"
                        )
                )
        );

        request.setUpdatedAt(
                toIsoTimestamp(
                        result.getTimestamp(
                                "Updated_At"
                        )
                )
        );

        return request;
    }

    private Integer getNullableInteger(
            ResultSet result,
            String columnName
    ) throws SQLException {

        int value =
                result.getInt(columnName);

        return result.wasNull()
                ? null
                : value;
    }

    private String toIsoDate(
            Date value
    ) {
        return value == null
                ? null
                : value.toLocalDate().toString();
    }

    private String toIsoTimestamp(
            Timestamp value
    ) {
        return value == null
                ? null
                : value.toLocalDateTime().toString();
    }
}