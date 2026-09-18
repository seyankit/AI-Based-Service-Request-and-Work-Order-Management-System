package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import java.sql.*;
import java.time.Instant;

public class EmailVerificationDAO {
    public record PendingVerification(int verificationId,int personnelId,String email,String firstName,Instant expiresAt) {}

    public void replaceToken(int personnelId,String tokenHash,Instant expiresAt) throws SQLException {
        try(Connection c=DatabaseConnection.getConnection()) {
            boolean ac=c.getAutoCommit(); c.setAutoCommit(false);
            try {
                try(PreparedStatement revoke=c.prepareStatement("UPDATE EMAIL_VERIFICATION_TOKEN SET Revoked_At=CURRENT_TIMESTAMP WHERE Personnel_ID=? AND Used_At IS NULL AND Revoked_At IS NULL")) { revoke.setInt(1,personnelId); revoke.executeUpdate(); }
                try(PreparedStatement ins=c.prepareStatement("INSERT INTO EMAIL_VERIFICATION_TOKEN (Personnel_ID,Token_Hash,Expires_At) VALUES (?,?,?)")) {
                    ins.setInt(1,personnelId); ins.setString(2,tokenHash); ins.setTimestamp(3,Timestamp.from(expiresAt)); ins.executeUpdate();
                }
                c.commit();
            } catch(Exception e){ c.rollback(); if(e instanceof SQLException se) throw se; throw new SQLException(e); } finally { c.setAutoCommit(ac); }
        }
    }
    public boolean canResend(int personnelId) throws SQLException {
        String sql="SELECT MAX(Created_At) Last_Created FROM EMAIL_VERIFICATION_TOKEN WHERE Personnel_ID=?";
        try(Connection c=DatabaseConnection.getConnection(); PreparedStatement ps=c.prepareStatement(sql)){ ps.setInt(1,personnelId); try(ResultSet r=ps.executeQuery()){ if(!r.next()||r.getTimestamp(1)==null)return true; return r.getTimestamp(1).toInstant().isBefore(Instant.now().minusSeconds(60)); } }
    }
    public PendingVerification findValid(String tokenHash) throws SQLException {
        String sql="""
          SELECT evt.Verification_ID,evt.Personnel_ID,evt.Expires_At,sp.Email,sp.First_Name
          FROM EMAIL_VERIFICATION_TOKEN evt JOIN SCHOOL_PERSONNEL sp ON sp.Personnel_ID=evt.Personnel_ID
          WHERE evt.Token_Hash=? AND evt.Used_At IS NULL AND evt.Revoked_At IS NULL AND evt.Expires_At>CURRENT_TIMESTAMP
          """;
        try(Connection c=DatabaseConnection.getConnection(); PreparedStatement ps=c.prepareStatement(sql)){ ps.setString(1,tokenHash); try(ResultSet r=ps.executeQuery()){ if(!r.next()) return null; return new PendingVerification(r.getInt(1),r.getInt(2),r.getString(4),r.getString(5),r.getTimestamp(3).toInstant()); } }
    }
    public int resolveTrustedRole(Connection c,String email) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT Role_ID FROM INSTITUTIONAL_ROLE_ASSIGNMENT WHERE LOWER(Email)=LOWER(?) AND Active=1")){ ps.setString(1,email); try(ResultSet r=ps.executeQuery()){ return r.next()?r.getInt(1):1; } }
    }
    public String verifyAndActivate(PendingVerification v) throws SQLException {
        try(Connection c=DatabaseConnection.getConnection()) {
            boolean ac=c.getAutoCommit(); c.setAutoCommit(false);
            try {
                int role=resolveTrustedRole(c,v.email());
                try(PreparedStatement used=c.prepareStatement("UPDATE EMAIL_VERIFICATION_TOKEN SET Used_At=CURRENT_TIMESTAMP WHERE Verification_ID=? AND Used_At IS NULL AND Revoked_At IS NULL")){ used.setInt(1,v.verificationId()); if(used.executeUpdate()!=1) throw new SQLException("Verification token already used."); }
                try(PreparedStatement rolePs=c.prepareStatement("INSERT INTO PERSONNEL_ROLE_ASSIGNMENT (Personnel_ID,Role_ID) VALUES (?,?) ON DUPLICATE KEY UPDATE Role_ID=VALUES(Role_ID), Assigned_At=CURRENT_TIMESTAMP")){ rolePs.setInt(1,v.personnelId()); rolePs.setInt(2,role); rolePs.executeUpdate(); }
                String status=role==1?"Active":"Pending Approval";
                try(PreparedStatement up=c.prepareStatement("UPDATE SCHOOL_PERSONNEL SET Email_Verified_At=CURRENT_TIMESTAMP, Account_Status=? WHERE Personnel_ID=?")){ up.setString(1,status); up.setInt(2,v.personnelId()); up.executeUpdate(); }
                if(role!=1){ try(PreparedStatement ar=c.prepareStatement("INSERT INTO ACCOUNT_REVIEW (Personnel_ID,Requested_Role_ID,Requested_Department_ID,Review_Status) SELECT Personnel_ID,?,Department_ID,'Pending' FROM SCHOOL_PERSONNEL WHERE Personnel_ID=? ON DUPLICATE KEY UPDATE Requested_Role_ID=VALUES(Requested_Role_ID),Review_Status='Pending'")){ ar.setInt(1,role); ar.setInt(2,v.personnelId()); ar.executeUpdate(); } }
                c.commit(); return status;
            } catch(Exception e){ c.rollback(); if(e instanceof SQLException se) throw se; throw new SQLException(e); } finally { c.setAutoCommit(ac); }
        }
    }
}
