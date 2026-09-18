package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.model.PersonnelAccessRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PersonnelManagementDAO {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAXIMUM_LIMIT = 250;

    public List<PersonnelAccessRecord> findAll(
            int limit
    ) throws SQLException {

        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException(
                    "The personnel limit must be from 1 to "
                            + MAXIMUM_LIMIT
                            + "."
            );
        }

        String sql = """
                SELECT
                    sp.Personnel_ID,
                    sp.First_Name,
                    sp.Middle_Name,
                    sp.Last_Name,
                    sp.Suffix,
                    sp.Email,
                    sp.Contact_Number,
                    sp.Personnel_Type,
                    sp.Account_Status,
                    sp.Email_Verified_At,
                    sp.Department_ID,
                    sp.Profile_Image_File_Name,
                    d.Department_Name,
                    pra.Role_ID,
                    pr.Role_Name
                FROM SCHOOL_PERSONNEL sp
                INNER JOIN DEPARTMENT d
                    ON d.Department_ID = sp.Department_ID
                LEFT JOIN PERSONNEL_ROLE_ASSIGNMENT pra
                    ON pra.Personnel_ID = sp.Personnel_ID
                LEFT JOIN PERSONNEL_ROLE pr
                    ON pr.Role_ID = pra.Role_ID
                ORDER BY
                    sp.Last_Name,
                    sp.First_Name,
                    sp.Personnel_ID
                LIMIT ?
                """;

        List<PersonnelAccessRecord> personnel =
                new ArrayList<>();

        try (
            Connection connection =
                    DatabaseConnection.getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(1, limit);

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                while (resultSet.next()) {
                    personnel.add(
                            map(resultSet)
                    );
                }
            }
        }

        return personnel;
    }



    public PersonnelAccessRecord findById(
            int personnelId
    ) throws SQLException {

        if (personnelId <= 0) {
            throw new IllegalArgumentException(
                    "A valid personnel ID is required."
            );
        }

        String sql = """
                SELECT
                    sp.Personnel_ID,
                    sp.First_Name,
                    sp.Middle_Name,
                    sp.Last_Name,
                    sp.Suffix,
                    sp.Email,
                    sp.Contact_Number,
                    sp.Personnel_Type,
                    sp.Account_Status,
                    sp.Email_Verified_At,
                    sp.Department_ID,
                    sp.Profile_Image_File_Name,
                    d.Department_Name,
                    pra.Role_ID,
                    pr.Role_Name
                FROM SCHOOL_PERSONNEL sp
                INNER JOIN DEPARTMENT d
                    ON d.Department_ID = sp.Department_ID
                LEFT JOIN PERSONNEL_ROLE_ASSIGNMENT pra
                    ON pra.Personnel_ID = sp.Personnel_ID
                LEFT JOIN PERSONNEL_ROLE pr
                    ON pr.Role_ID = pra.Role_ID
                WHERE sp.Personnel_ID = ?
                LIMIT 1
                """;

        try (
            Connection connection =
                    DatabaseConnection.getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    personnelId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                return resultSet.next()
                        ? map(resultSet)
                        : null;
            }
        }
    }
    public void updatePersonnelAccess(
            int actorPersonnelId,
            int actorRoleId,
            int targetPersonnelId,
            int newDepartmentId,
            int newRoleId,
            String clientIpAddress,
            String clientUserAgent
    ) throws SQLException {

        if (actorPersonnelId <= 0) {
            throw new IllegalArgumentException(
                    "A valid administrator account is required."
            );
        }

        if (targetPersonnelId <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid personnel account."
            );
        }

        if (actorPersonnelId == targetPersonnelId) {
            throw new IllegalArgumentException(
                    "You cannot change your own personnel access assignment."
            );
        }

        if (newDepartmentId <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid department."
            );
        }

        if (newRoleId < 1 || newRoleId > 4) {
            throw new IllegalArgumentException(
                    "Select a supported system role."
            );
        }

        try (
            Connection connection =
                    DatabaseConnection.getConnection()
        ) {
            boolean originalAutoCommit =
                    connection.getAutoCommit();

            connection.setAutoCommit(false);

            try {
                requireActiveServiceAdministrator(
                        connection,
                        actorPersonnelId,
                        actorRoleId
                );

                requireDepartmentExists(
                        connection,
                        newDepartmentId
                );

                requireRoleExists(
                        connection,
                        newRoleId
                );

                TargetAccessState target =
                        loadAndLockTarget(
                                connection,
                                targetPersonnelId
                        );

                validatePrivilegedAssignment(
                        target,
                        newRoleId
                );

                updateDepartment(
                        connection,
                        targetPersonnelId,
                        newDepartmentId
                );

                updateRoleAssignment(
                        connection,
                        targetPersonnelId,
                        newRoleId
                );

                synchronizeInstitutionalAssignment(
                        connection,
                        target.email(),
                        newDepartmentId,
                        newRoleId
                );

                insertPersonnelAccessAuditLog(
                        connection,
                        actorPersonnelId,
                        actorRoleId,
                        targetPersonnelId,
                        target.departmentId(),
                        newDepartmentId,
                        target.roleId(),
                        newRoleId,
                        clientIpAddress,
                        clientUserAgent
                );

                connection.commit();

            } catch (
                    SQLException
                    | IllegalArgumentException
                    | SecurityException exception
            ) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(
                            rollbackException
                    );
                }

                throw exception;

            } finally {
                try {
                    connection.setAutoCommit(
                            originalAutoCommit
                    );
                } catch (SQLException ignored) {
                    // Closing the connection is the final safeguard.
                }
            }
        }
    }

    private void requireActiveServiceAdministrator(
            Connection connection,
            int actorPersonnelId,
            int sessionRoleId
    ) throws SQLException {

        if (sessionRoleId != 2) {
            throw new SecurityException(
                    "Service Administrator access is required."
            );
        }

        String sql = """
                SELECT
                    sp.Account_Status,
                    pra.Role_ID
                FROM SCHOOL_PERSONNEL sp
                INNER JOIN PERSONNEL_ROLE_ASSIGNMENT pra
                    ON pra.Personnel_ID = sp.Personnel_ID
                WHERE sp.Personnel_ID = ?
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    actorPersonnelId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    throw new SecurityException(
                            "The administrator account is no longer available."
                    );
                }

                String accountStatus =
                        resultSet.getString(
                                "Account_Status"
                        );

                int databaseRoleId =
                        resultSet.getInt(
                                "Role_ID"
                        );

                if (
                    databaseRoleId != 2
                    || !"Active".equalsIgnoreCase(
                            String.valueOf(accountStatus)
                    )
                ) {
                    throw new SecurityException(
                            "The current account no longer has active Service Administrator access."
                    );
                }
            }
        }
    }

    private void requireDepartmentExists(
            Connection connection,
            int departmentId
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM DEPARTMENT
                WHERE Department_ID = ?
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    departmentId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "The selected department does not exist."
                    );
                }
            }
        }
    }

    private void requireRoleExists(
            Connection connection,
            int roleId
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM PERSONNEL_ROLE
                WHERE Role_ID = ?
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    roleId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "The selected system role does not exist."
                    );
                }
            }
        }
    }

    private TargetAccessState loadAndLockTarget(
            Connection connection,
            int personnelId
    ) throws SQLException {

        String sql = """
                SELECT
                    sp.Email,
                    sp.Email_Verified_At,
                    sp.Account_Status,
                    sp.Department_ID,
                    pra.Role_ID
                FROM SCHOOL_PERSONNEL sp
                LEFT JOIN PERSONNEL_ROLE_ASSIGNMENT pra
                    ON pra.Personnel_ID = sp.Personnel_ID
                WHERE sp.Personnel_ID = ?
                FOR UPDATE
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    personnelId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "The selected personnel account does not exist."
                    );
                }

                int roleValue =
                        resultSet.getInt(
                                "Role_ID"
                        );

                Integer roleId =
                        resultSet.wasNull()
                                ? null
                                : roleValue;

                Timestamp verifiedAt =
                        resultSet.getTimestamp(
                                "Email_Verified_At"
                        );

                return new TargetAccessState(
                        resultSet.getString(
                                "Email"
                        ),
                        resultSet.getString(
                                "Account_Status"
                        ),
                        verifiedAt != null,
                        resultSet.getInt(
                                "Department_ID"
                        ),
                        roleId
                );
            }
        }
    }

    private void validatePrivilegedAssignment(
            TargetAccessState target,
            int newRoleId
    ) {

        if (newRoleId == 1) {
            return;
        }

        if (target.emailVerified()) {
            return;
        }

        /*
         * Legacy accounts created before email verification was
         * introduced may have no Email_Verified_At value. Existing
         * institutionally active/inactive records are allowed so
         * older test/administrative accounts remain manageable.
         */
        String status =
                target.accountStatus() == null
                        ? ""
                        : target.accountStatus().trim();

        if (
            !"Active".equalsIgnoreCase(status)
            && !"Inactive".equalsIgnoreCase(status)
        ) {
            throw new IllegalArgumentException(
                    "Privileged roles can only be assigned to a verified personnel account."
            );
        }
    }

    private void updateDepartment(
            Connection connection,
            int personnelId,
            int departmentId
    ) throws SQLException {

        String sql = """
                UPDATE SCHOOL_PERSONNEL
                SET Department_ID = ?
                WHERE Personnel_ID = ?
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    departmentId
            );

            statement.setInt(
                    2,
                    personnelId
            );

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "Personnel department update did not affect exactly one row."
                );
            }
        }
    }

    private void updateRoleAssignment(
            Connection connection,
            int personnelId,
            int roleId
    ) throws SQLException {

        String sql = """
                INSERT INTO PERSONNEL_ROLE_ASSIGNMENT (
                    Personnel_ID,
                    Role_ID
                )
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE
                    Role_ID = ?,
                    Assigned_At = CURRENT_TIMESTAMP
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    personnelId
            );

            statement.setInt(
                    2,
                    roleId
            );

            statement.setInt(
                    3,
                    roleId
            );

            statement.executeUpdate();
        }
    }

    private void synchronizeInstitutionalAssignment(
            Connection connection,
            String email,
            int departmentId,
            int roleId
    ) throws SQLException {

        if (roleId == 1) {
            String sql = """
                    UPDATE INSTITUTIONAL_ROLE_ASSIGNMENT
                    SET
                        Department_ID = ?,
                        Active = 0
                    WHERE LOWER(Email) = LOWER(?)
                    """;

            try (
                PreparedStatement statement =
                        connection.prepareStatement(sql)
            ) {
                statement.setInt(
                        1,
                        departmentId
                );

                statement.setString(
                        2,
                        email
                );

                statement.executeUpdate();
            }

            return;
        }

        String updateSql = """
                UPDATE INSTITUTIONAL_ROLE_ASSIGNMENT
                SET
                    Role_ID = ?,
                    Department_ID = ?,
                    Active = 1
                WHERE LOWER(Email) = LOWER(?)
                """;

        int updated;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(
                            updateSql
                    )
        ) {
            statement.setInt(
                    1,
                    roleId
            );

            statement.setInt(
                    2,
                    departmentId
            );

            statement.setString(
                    3,
                    email
            );

            updated =
                    statement.executeUpdate();
        }

        if (updated > 0) {
            return;
        }

        String insertSql = """
                INSERT INTO INSTITUTIONAL_ROLE_ASSIGNMENT (
                    Email,
                    Role_ID,
                    Department_ID,
                    Active
                )
                VALUES (?, ?, ?, 1)
                """;

        try (
            PreparedStatement statement =
                    connection.prepareStatement(
                            insertSql
                    )
        ) {
            statement.setString(
                    1,
                    email
            );

            statement.setInt(
                    2,
                    roleId
            );

            statement.setInt(
                    3,
                    departmentId
            );

            statement.executeUpdate();
        }
    }

    private void insertPersonnelAccessAuditLog(
            Connection connection,
            int actorPersonnelId,
            int actorRoleId,
            int targetPersonnelId,
            int oldDepartmentId,
            int newDepartmentId,
            Integer oldRoleId,
            int newRoleId,
            String clientIpAddress,
            String clientUserAgent
    ) throws SQLException {

        String sql = """
                INSERT INTO AUDIT_LOG (
                    Actor_Type,
                    Actor_ID,
                    Actor_Role_ID,
                    Request_ID,
                    Work_Order_ID,
                    Action_Type,
                    Entity_Type,
                    Entity_ID,
                    Action_Outcome,
                    Action_Summary,
                    Details_JSON,
                    Client_IP_Address,
                    Client_User_Agent,
                    Correlation_ID
                )
                VALUES (
                    'PERSONNEL',
                    ?,
                    ?,
                    NULL,
                    NULL,
                    'PERSONNEL_ACCESS_UPDATED',
                    'PERSONNEL_ACCESS',
                    ?,
                    'SUCCESS',
                    ?,
                    JSON_OBJECT(
                        'personnelId', ?,
                        'oldRoleId', ?,
                        'newRoleId', ?,
                        'oldDepartmentId', ?,
                        'newDepartmentId', ?,
                        'institutionalAssignmentActive', ?
                    ),
                    ?,
                    ?,
                    ?
                )
                """;

        String summary =
                "Service Administrator updated personnel access for personnel "
                        + targetPersonnelId
                        + ".";

        try (
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setInt(
                    1,
                    actorPersonnelId
            );

            statement.setInt(
                    2,
                    actorRoleId
            );

            statement.setInt(
                    3,
                    targetPersonnelId
            );

            statement.setString(
                    4,
                    summary
            );

            statement.setInt(
                    5,
                    targetPersonnelId
            );

            statement.setObject(
                    6,
                    oldRoleId
            );

            statement.setInt(
                    7,
                    newRoleId
            );

            statement.setInt(
                    8,
                    oldDepartmentId
            );

            statement.setInt(
                    9,
                    newDepartmentId
            );

            statement.setInt(
                    10,
                    newRoleId == 1
                            ? 0
                            : 1
            );

            statement.setString(
                    11,
                    limitNullableText(
                            clientIpAddress,
                            45
                    )
            );

            statement.setString(
                    12,
                    limitNullableText(
                            clientUserAgent,
                            500
                    )
            );

            statement.setString(
                    13,
                    UUID.randomUUID()
                            .toString()
            );

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "Personnel access audit log was not created."
                );
            }
        }
    }

    private String limitNullableText(
            String value,
            int maximumLength
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized.length() <= maximumLength
                ? normalized
                : normalized.substring(
                        0,
                        maximumLength
                );
    }

    private record TargetAccessState(
            String email,
            String accountStatus,
            boolean emailVerified,
            int departmentId,
            Integer roleId
    ) {
    }
    private PersonnelAccessRecord map(
            ResultSet resultSet
    ) throws SQLException {

        PersonnelAccessRecord record =
                new PersonnelAccessRecord();

        record.setPersonnelId(
                resultSet.getInt(
                        "Personnel_ID"
                )
        );

        record.setFullName(
                buildFullName(
                        resultSet.getString(
                                "First_Name"
                        ),
                        resultSet.getString(
                                "Middle_Name"
                        ),
                        resultSet.getString(
                                "Last_Name"
                        ),
                        resultSet.getString(
                                "Suffix"
                        )
                )
        );

        record.setEmail(
                resultSet.getString(
                        "Email"
                )
        );

        record.setContactNumber(
                resultSet.getString(
                        "Contact_Number"
                )
        );

        record.setPersonnelType(
                resultSet.getString(
                        "Personnel_Type"
                )
        );

        record.setAccountStatus(
                resultSet.getString(
                        "Account_Status"
                )
        );

        record.setDepartmentId(
                resultSet.getInt(
                        "Department_ID"
                )
        );

        record.setDepartmentName(
                resultSet.getString(
                        "Department_Name"
                )
        );

        int roleId =
                resultSet.getInt(
                        "Role_ID"
                );

        record.setRoleId(
                resultSet.wasNull()
                        ? null
                        : roleId
        );

        record.setRoleName(
                resultSet.getString(
                        "Role_Name"
                )
        );

        Timestamp verifiedAt =
                resultSet.getTimestamp(
                        "Email_Verified_At"
                );

        record.setEmailVerifiedAt(
                verifiedAt == null
                        ? null
                        : verifiedAt
                                .toInstant()
                                .toString()
        );

        record.setProfileImageFileName(
                resultSet.getString(
                        "Profile_Image_File_Name"
                )
        );

        return record;
    }

    private String buildFullName(
            String firstName,
            String middleName,
            String lastName,
            String suffix
    ) {
        StringBuilder value =
                new StringBuilder();

        appendNamePart(
                value,
                firstName
        );

        appendNamePart(
                value,
                middleName
        );

        appendNamePart(
                value,
                lastName
        );

        appendNamePart(
                value,
                suffix
        );

        return value.toString();
    }

    private void appendNamePart(
            StringBuilder value,
            String part
    ) {
        if (
            part == null
            || part.isBlank()
        ) {
            return;
        }

        if (!value.isEmpty()) {
            value.append(' ');
        }

        value.append(
                part.trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        )
        );
    }
}
