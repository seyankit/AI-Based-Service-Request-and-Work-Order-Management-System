package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.util.WorkflowPolicy;
import java.sql.*;
import java.util.*;
import static ph.edu.htcgsc.serviceportal.dao.WorkflowStore.*;

public final class ApprovalDAO {
    public List<Map<String, Object>> list(int actor, int role, String status) throws SQLException {
        if (role != 2 && role != 3) throw new SecurityException("Approval review access is required.");
        try (Connection connection = DatabaseConnection.getConnection()) {
            int department = activeActor(connection, actor, role);
            return query(connection, """
                SELECT a.Approval_ID approvalId,a.Request_ID requestId,sr.Request_Number requestNumber,
                  a.Decision decision,a.Decided_At decisionDate,a.Decision_Remarks remarks,a.Requested_At requestedAt,
                  a.Department_ID departmentId,d.Department_Name departmentName,a.Approver_ID approverId,
                  CONCAT(p.First_Name,' ',p.Last_Name) requesterName,sr.Request_Title title,
                  sr.Request_Description description,sr.Request_Location location,sr.Current_Status currentStatus,
                  COALESCE(sr.Final_Priority,sr.Preferred_Priority) priority,c.Category_Name category
                FROM REQUEST_APPROVAL a JOIN SERVICE_REQUEST sr ON sr.Request_ID=a.Request_ID
                  JOIN SCHOOL_PERSONNEL p ON p.Personnel_ID=sr.Requester_ID
                  JOIN DEPARTMENT d ON d.Department_ID=a.Department_ID
                  LEFT JOIN SERVICE_CATEGORY c ON c.Category_ID=COALESCE(sr.Final_Category_ID,sr.Requested_Category_ID)
                WHERE (?=2 OR a.Department_ID=?) AND (?='' OR a.Decision=?)
                ORDER BY a.Requested_At DESC,a.Approval_ID DESC LIMIT 250
                """, role, department, status, status);
        }
    }

    public void decide(int actor, int role, long approvalId, String decision, String remarks) throws SQLException {
        if (!Set.of("Approved", "Rejected").contains(decision)) throw new IllegalArgumentException("Decision must be Approved or Rejected.");
        remarks = WorkflowPolicy.text(remarks, "Decision remarks", 3, 1000);
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int department = activeActor(connection, actor, role);
                // Always lock request before approval, matching the routing/cancellation lock order.
                Map<String, Object> ref = one(connection, "SELECT Request_ID requestId FROM REQUEST_APPROVAL WHERE Approval_ID=?", approvalId);
                long requestId = number(ref, "requestId");
                Map<String, Object> request = one(connection, "SELECT Requester_ID requesterId,Current_Status status,Request_Number number FROM SERVICE_REQUEST WHERE Request_ID=? FOR UPDATE", requestId);
                Map<String, Object> approval = one(connection, "SELECT Department_ID departmentId,Decision decision,Is_Current currentApproval FROM REQUEST_APPROVAL WHERE Approval_ID=? AND Is_Current=TRUE FOR UPDATE", approvalId);
                WorkflowPolicy.requireApproval(role, department, (int) number(approval, "departmentId"),
                        (int) number(request, "requesterId"), actor, string(request, "status"), string(approval, "decision"));
                update(connection, "UPDATE REQUEST_APPROVAL SET Decision=?,Approver_ID=?,Decision_Remarks=?,Decided_At=CURRENT_TIMESTAMP WHERE Approval_ID=?", decision, actor, remarks, approvalId);
                requestStatus(connection, requestId, "Awaiting Approval", decision, actor, role, remarks);
                String event = "APPROVAL:" + approvalId + ":" + decision;
                String message = string(request, "number") + " was " + decision.toLowerCase(Locale.ROOT) + ". " + remarks;
                WorkflowStore.notify(connection, (int) number(request, "requesterId"), actor, requestId, null,
                        "REQUEST_" + decision.toUpperCase(Locale.ROOT), "Request " + decision.toLowerCase(Locale.ROOT), message, event);
                notifyRole(connection, 2, null, actor, requestId, null, "REQUEST_" + decision.toUpperCase(Locale.ROOT), "Request decision recorded", message, event);
                audit(connection, actor, role, requestId, null, "REQUEST_" + decision.toUpperCase(Locale.ROOT), "Department Head " + decision.toLowerCase(Locale.ROOT) + " request.", remarks);
                connection.commit();
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        }
    }
}

