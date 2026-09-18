package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.model.AccountReview;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public final class AccountReviewDAO {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAXIMUM_LIMIT = 100;

    public List<AccountReview> findPendingReviews(
            int limit
    ) throws SQLException {

        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException(
                    "The review limit must be from 1 to "
                            + MAXIMUM_LIMIT
                            + "."
            );
        }

        String sql = """
                SELECT
                    ar.Account_Review_ID,
                    ar.Personnel_ID,

                    sp.First_Name,
                    sp.Last_Name,
                    sp.Email,
                    sp.Contact_Number,
                    sp.Personnel_Type,

                    ar.Requested_Role_ID,
                    pr.Role_Name AS Requested_Role_Name,

                    ar.Requested_Department_ID,
                    d.Department_Name
                        AS Requested_Department_Name,

                    ar.Review_Status,
                    ar.Reviewed_By_ID,
                    ar.Decision_Remarks,
                    ar.Submitted_At,
                    ar.Reviewed_At,
                    ar.Updated_At

                FROM ACCOUNT_REVIEW ar

                INNER JOIN SCHOOL_PERSONNEL sp
                    ON sp.Personnel_ID =
                       ar.Personnel_ID

                INNER JOIN PERSONNEL_ROLE pr
                    ON pr.Role_ID =
                       ar.Requested_Role_ID

                INNER JOIN DEPARTMENT d
                    ON d.Department_ID =
                       ar.Requested_Department_ID

                WHERE ar.Review_Status = 'Pending'
                  AND sp.Account_Status IN ('Pending', 'Pending Approval')

                ORDER BY
                    ar.Submitted_At ASC,
                    ar.Account_Review_ID ASC

                LIMIT ?
                """;

        List<AccountReview> reviews =
                new ArrayList<>();

        try (
            Connection connection =
                    DatabaseConnection.getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    limit
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                while (result.next()) {
                    reviews.add(
                            mapAccountReview(result)
                    );
                }
            }
        }

        return reviews;
    }

    private AccountReview mapAccountReview(
            ResultSet result
    ) throws SQLException {

        Integer reviewedById =
                getNullableInteger(
                        result,
                        "Reviewed_By_ID"
                );

        return new AccountReview(
                result.getLong(
                        "Account_Review_ID"
                ),
                result.getInt(
                        "Personnel_ID"
                ),
                result.getString(
                        "First_Name"
                ),
                result.getString(
                        "Last_Name"
                ),
                result.getString(
                        "Email"
                ),
                result.getString(
                        "Contact_Number"
                ),
                result.getString(
                        "Personnel_Type"
                ),
                result.getInt(
                        "Requested_Role_ID"
                ),
                result.getString(
                        "Requested_Role_Name"
                ),
                result.getInt(
                        "Requested_Department_ID"
                ),
                result.getString(
                        "Requested_Department_Name"
                ),
                result.getString(
                        "Review_Status"
                ),
                reviewedById,
                result.getString(
                        "Decision_Remarks"
                ),
                toIsoTimestamp(
                        result.getTimestamp(
                                "Submitted_At"
                        )
                ),
                toIsoTimestamp(
                        result.getTimestamp(
                                "Reviewed_At"
                        )
                ),
                toIsoTimestamp(
                        result.getTimestamp(
                                "Updated_At"
                        )
                )
        );
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

    private String toIsoTimestamp(
            Timestamp timestamp
    ) {
        if (timestamp == null) {
            return null;
        }

        return timestamp
                .toLocalDateTime()
                .withNano(0)
                .toString();
    }
}