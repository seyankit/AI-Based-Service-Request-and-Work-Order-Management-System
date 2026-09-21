package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read-only, aggregate-only operational insights for Service Administrators. */
public final class ServiceInsightsDAO {

    public enum Period {
        LAST_7_DAYS("7d"),
        LAST_30_DAYS("30d"),
        CURRENT_MONTH("month"),
        ALL_TIME("all");

        private final String value;

        Period(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static Period fromValue(String value) {
            for (Period period : values()) {
                if (period.value.equals(value)) {
                    return period;
                }
            }
            throw new IllegalArgumentException("The period parameter is invalid.");
        }
    }

    public record Summary(long totalRequests, long submittedRequests,
                          long awaitingApprovalRequests, long approvedRequests,
                          long duplicateRequests, long totalWorkOrders,
                          long completedWorkOrders, BigDecimal averageCompletionHours) {
    }

    public record CategoryCount(Long categoryId, String categoryName, long count) {
    }

    public record PriorityCount(String priority, long count) {
    }

    public record WorkOrderStatusCount(String status, long count) {
    }

    public record TrendPoint(String bucket, long count) {
    }

    public record Insights(Summary summary, List<CategoryCount> requestsByCategory,
                           List<PriorityCount> requestsByPriority,
                           List<WorkOrderStatusCount> workOrdersByStatus,
                           List<TrendPoint> requestTrend) {
        public Insights {
            requestsByCategory = List.copyOf(requestsByCategory);
            requestsByPriority = List.copyOf(requestsByPriority);
            workOrdersByStatus = List.copyOf(workOrdersByStatus);
            requestTrend = List.copyOf(requestTrend);
        }
    }

    public Insights load(Period period) throws SQLException {
        if (period == null) {
            throw new IllegalArgumentException("The period parameter is required.");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            Summary summary = loadSummary(connection, period);
            return new Insights(
                    summary,
                    loadCategories(connection, period),
                    loadPriorities(connection, period),
                    loadWorkOrderStatuses(connection, period),
                    loadTrend(connection, period)
            );
        }
    }

    private Summary loadSummary(Connection connection, Period period) throws SQLException {
        String requestWindow = requestWindow(period, "sr");
        String workOrderWindow = workOrderWindow(period, "wo");
        String requestSql = """
                SELECT COUNT(*) AS totalRequests,
                       COALESCE(SUM(sr.Current_Status = 'Submitted'), 0) AS submittedRequests,
                       COALESCE(SUM(sr.Current_Status = 'Awaiting Approval'), 0) AS awaitingApprovalRequests,
                       COALESCE(SUM(sr.Current_Status = 'Approved'), 0) AS approvedRequests,
                       COALESCE(SUM(sr.Current_Status = 'Duplicate'), 0) AS duplicateRequests
                FROM SERVICE_REQUEST sr
                WHERE %s
                """.formatted(requestWindow);
        String workOrderSql = """
                SELECT COUNT(*) AS totalWorkOrders,
                       COALESCE(SUM(wo.Work_Status IN ('Completed', 'Verified')), 0) AS completedWorkOrders,
                       AVG(CASE
                           WHEN wo.Actual_Start_At IS NOT NULL
                            AND wo.Completed_At IS NOT NULL
                            AND wo.Completed_At >= wo.Actual_Start_At
                           THEN TIMESTAMPDIFF(SECOND, wo.Actual_Start_At, wo.Completed_At) / 3600.0
                           ELSE NULL
                       END) AS averageCompletionHours
                FROM WORK_ORDER wo
                WHERE %s
                """.formatted(workOrderWindow);

        long totalRequests;
        long submittedRequests;
        long awaitingApprovalRequests;
        long approvedRequests;
        long duplicateRequests;
        try (PreparedStatement statement = connection.prepareStatement(requestSql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            totalRequests = result.getLong("totalRequests");
            submittedRequests = result.getLong("submittedRequests");
            awaitingApprovalRequests = result.getLong("awaitingApprovalRequests");
            approvedRequests = result.getLong("approvedRequests");
            duplicateRequests = result.getLong("duplicateRequests");
        }

        try (PreparedStatement statement = connection.prepareStatement(workOrderSql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return new Summary(totalRequests, submittedRequests, awaitingApprovalRequests,
                    approvedRequests, duplicateRequests, result.getLong("totalWorkOrders"),
                    result.getLong("completedWorkOrders"), result.getBigDecimal("averageCompletionHours"));
        }
    }

    private List<CategoryCount> loadCategories(Connection connection, Period period) throws SQLException {
        String sql = """
                SELECT category.Category_ID AS categoryId,
                       COALESCE(category.Category_Name, 'Uncategorized') AS categoryName,
                       COUNT(*) AS itemCount
                FROM SERVICE_REQUEST sr
                LEFT JOIN SERVICE_CATEGORY category
                    ON category.Category_ID = COALESCE(sr.Final_Category_ID, sr.Requested_Category_ID)
                WHERE %s
                GROUP BY category.Category_ID, category.Category_Name
                ORDER BY itemCount DESC, categoryName ASC, categoryId ASC
                """.formatted(requestWindow(period, "sr"));
        List<CategoryCount> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                long id = result.getLong("categoryId");
                results.add(new CategoryCount(result.wasNull() ? null : id,
                        result.getString("categoryName"), result.getLong("itemCount")));
            }
        }
        return results;
    }

    private List<PriorityCount> loadPriorities(Connection connection, Period period) throws SQLException {
        String sql = """
                SELECT COALESCE(sr.Final_Priority, sr.Preferred_Priority) AS priority,
                       COUNT(*) AS itemCount
                FROM SERVICE_REQUEST sr
                WHERE %s
                GROUP BY COALESCE(sr.Final_Priority, sr.Preferred_Priority)
                ORDER BY itemCount DESC, priority ASC
                """.formatted(requestWindow(period, "sr"));
        List<PriorityCount> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                results.add(new PriorityCount(result.getString("priority"), result.getLong("itemCount")));
            }
        }
        return results;
    }

    private List<WorkOrderStatusCount> loadWorkOrderStatuses(Connection connection, Period period) throws SQLException {
        String sql = """
                SELECT wo.Work_Status AS status, COUNT(*) AS itemCount
                FROM WORK_ORDER wo
                WHERE %s
                GROUP BY wo.Work_Status
                ORDER BY CASE wo.Work_Status
                             WHEN 'Created' THEN 1 WHEN 'Assigned' THEN 2
                             WHEN 'Acknowledged' THEN 3 WHEN 'In Progress' THEN 4
                             WHEN 'On Hold' THEN 5 WHEN 'Completed' THEN 6
                             WHEN 'Verified' THEN 7 ELSE 8 END,
                         status ASC
                """.formatted(workOrderWindow(period, "wo"));
        List<WorkOrderStatusCount> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                results.add(new WorkOrderStatusCount(result.getString("status"), result.getLong("itemCount")));
            }
        }
        return results;
    }

    private List<TrendPoint> loadTrend(Connection connection, Period period) throws SQLException {
        String bucket = period == Period.ALL_TIME
                ? "DATE_FORMAT(sr.Created_At, '%Y-%m')"
                : "DATE_FORMAT(sr.Created_At, '%Y-%m-%d')";
        String sql = """
                SELECT %s AS bucket, COUNT(*) AS itemCount
                FROM SERVICE_REQUEST sr
                WHERE %s
                GROUP BY %s
                ORDER BY bucket ASC
                """.formatted(bucket, requestWindow(period, "sr"), bucket);
        List<TrendPoint> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                results.add(new TrendPoint(result.getString("bucket"), result.getLong("itemCount")));
            }
        }
        return results;
    }

    private String requestWindow(Period period, String alias) {
        return window(period, alias + ".Created_At");
    }

    private String workOrderWindow(Period period, String alias) {
        return window(period, alias + ".Created_At");
    }

    private String window(Period period, String column) {
        return switch (period) {
            case LAST_7_DAYS -> column + " >= CURRENT_TIMESTAMP - INTERVAL 7 DAY";
            case LAST_30_DAYS -> column + " >= CURRENT_TIMESTAMP - INTERVAL 30 DAY";
            case CURRENT_MONTH -> column + " >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')";
            case ALL_TIME -> "1 = 1";
        };
    }
}
