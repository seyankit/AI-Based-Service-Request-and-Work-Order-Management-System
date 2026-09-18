package ph.edu.htcgsc.serviceportal.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared object authorization. SQL fragments are selected only from server-owned constants. */
public final class ResourceAccess {
    private ResourceAccess() { }

    public static boolean canViewRequest(Connection connection, int actorId, int roleId, long requestId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM SERVICE_REQUEST sr WHERE sr.Request_ID=? AND " + scopeSql("sr", roleId))) {
            statement.setLong(1, requestId);
            statement.setInt(2, actorId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    /** Every returned predicate has exactly one placeholder for the authenticated personnel ID. */
    public static String scopeSql(String alias, int roleId) {
        if (!"sr".equals(alias)) throw new IllegalArgumentException("Unsupported request alias.");
        return switch (roleId) {
            case 1 -> "sr.Requester_ID=?";
            case 2 -> "EXISTS (SELECT 1 FROM SCHOOL_PERSONNEL actor JOIN PERSONNEL_ROLE_ASSIGNMENT role "
                    + "ON role.Personnel_ID=actor.Personnel_ID WHERE actor.Personnel_ID=? "
                    + "AND actor.Account_Status='Active' AND role.Role_ID=2)";
            case 3 -> "EXISTS (SELECT 1 FROM REQUEST_APPROVAL access_approval JOIN SCHOOL_PERSONNEL actor "
                    + "ON actor.Department_ID=access_approval.Department_ID "
                    + "WHERE access_approval.Request_ID=sr.Request_ID AND actor.Personnel_ID=?)";
            case 4 -> "EXISTS (SELECT 1 FROM WORK_ORDER access_work JOIN WORK_ORDER_ASSIGNMENT access_assignment "
                    + "ON access_assignment.Work_Order_ID=access_work.Work_Order_ID "
                    + "WHERE access_work.Request_ID=sr.Request_ID AND access_assignment.Technician_ID=? "
                    + "AND access_assignment.Is_Current=TRUE)";
            default -> "(? IS NULL AND FALSE)";
        };
    }
}
