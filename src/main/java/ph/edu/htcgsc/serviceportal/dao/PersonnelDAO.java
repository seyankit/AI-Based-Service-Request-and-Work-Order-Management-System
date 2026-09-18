package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.model.Personnel;

import java.sql.*;

public class PersonnelDAO {
    public boolean emailExists(String email) throws SQLException {
        try (Connection c=DatabaseConnection.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT 1 FROM SCHOOL_PERSONNEL WHERE LOWER(Email)=LOWER(?)")) {
            ps.setString(1,email); try(ResultSet r=ps.executeQuery()){ return r.next(); }
        }
    }
    public boolean departmentExists(int id) throws SQLException {
        try (Connection c=DatabaseConnection.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT 1 FROM DEPARTMENT WHERE Department_ID=?")) {
            ps.setInt(1,id); try(ResultSet r=ps.executeQuery()){ return r.next(); }
        }
    }
    public int createPendingVerificationPersonnel(Personnel p) throws SQLException {
        String sql="""
            INSERT INTO SCHOOL_PERSONNEL
            (First_Name, Middle_Name, Last_Name, Suffix, Email, Contact_Number, Personnel_Type, Password_Hash, Account_Status, Department_ID)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Pending Email Verification', ?)
            """;
        try(Connection c=DatabaseConnection.getConnection(); PreparedStatement ps=c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,p.getFirstName()); ps.setString(2,p.getMiddleName()); ps.setString(3,p.getLastName()); ps.setString(4,p.getSuffix());
            ps.setString(5,p.getEmail()); ps.setString(6,p.getContactNumber()); ps.setString(7,p.getPersonnelType()); ps.setString(8,p.getPasswordHash()); ps.setInt(9,p.getDepartmentId());
            if(ps.executeUpdate()!=1) throw new SQLException("Personnel insert failed.");
            try(ResultSet k=ps.getGeneratedKeys()){ if(!k.next()) throw new SQLException("Missing generated Personnel_ID."); return k.getInt(1); }
        }
    }
    public Personnel findByEmail(String email) throws SQLException { return find("LOWER(sp.Email)=LOWER(?)", email, null); }
    public Personnel findById(int id) throws SQLException { return find("sp.Personnel_ID=?", null, id); }
    private Personnel find(String where,String email,Integer id) throws SQLException {
        String sql=base()+" WHERE "+where;
        try(Connection c=DatabaseConnection.getConnection(); PreparedStatement ps=c.prepareStatement(sql)) {
            if(email!=null) ps.setString(1,email); else ps.setInt(1,id);
            try(ResultSet r=ps.executeQuery()){ if(!r.next()) return null; return map(r); }
        }
    }
    private String base(){ return """
        SELECT sp.Personnel_ID, sp.First_Name, sp.Middle_Name, sp.Last_Name, sp.Suffix,
               sp.Email, sp.Contact_Number, sp.Personnel_Type, sp.Password_Hash,
               sp.Account_Status, sp.Email_Verified_At, sp.Department_ID, sp.Profile_Image_File_Name, d.Department_Name,
               COALESCE(pra.Role_ID,0) AS Role_ID
        FROM SCHOOL_PERSONNEL sp
        INNER JOIN DEPARTMENT d ON d.Department_ID=sp.Department_ID
        LEFT JOIN PERSONNEL_ROLE_ASSIGNMENT pra ON pra.Personnel_ID=sp.Personnel_ID
        """; }
    private Personnel map(ResultSet r) throws SQLException {
        Personnel p=new Personnel();
        p.setPersonnelId(r.getInt("Personnel_ID")); p.setFirstName(r.getString("First_Name")); p.setMiddleName(r.getString("Middle_Name"));
        p.setLastName(r.getString("Last_Name")); p.setSuffix(r.getString("Suffix")); p.setEmail(r.getString("Email")); p.setContactNumber(r.getString("Contact_Number"));
        p.setPersonnelType(r.getString("Personnel_Type")); p.setPasswordHash(r.getString("Password_Hash")); p.setAccountStatus(r.getString("Account_Status"));
        Timestamp verified=r.getTimestamp("Email_Verified_At"); p.setEmailVerifiedAt(verified==null?null:verified.toInstant().toString());
        p.setDepartmentId(r.getInt("Department_ID")); p.setProfileImageFileName(r.getString("Profile_Image_File_Name")); p.setDepartmentName(r.getString("Department_Name")); p.setRoleId(r.getInt("Role_ID")); return p;
    }

    public boolean updateProfileImageFileName(
            int personnelId,
            String profileImageFileName
    ) throws SQLException {

        String sql = """
                UPDATE SCHOOL_PERSONNEL
                SET Profile_Image_File_Name = ?
                WHERE Personnel_ID = ?
                """;

        try (
            Connection connection =
                    DatabaseConnection.getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            if (
                profileImageFileName == null
                || profileImageFileName.isBlank()
            ) {
                statement.setNull(
                        1,
                        java.sql.Types.VARCHAR
                );
            } else {
                statement.setString(
                        1,
                        profileImageFileName.trim()
                );
            }

            statement.setInt(
                    2,
                    personnelId
            );

            return statement.executeUpdate() == 1;
        }
    }
}
