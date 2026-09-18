package ph.edu.htcgsc.serviceportal.model;

public class Personnel {
    private int personnelId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String suffix;
    private String email;
    private String contactNumber;
    private String personnelType;
    private String passwordHash;
    private String accountStatus;
    private int departmentId;
    private String departmentName;
    private int roleId;
    private String emailVerifiedAt;
    private String profileImageFileName;

    public int getPersonnelId() { return personnelId; }
    public void setPersonnelId(int personnelId) { this.personnelId = personnelId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getPersonnelType() { return personnelType; }
    public void setPersonnelType(String personnelType) { this.personnelType = personnelType; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
    public int getDepartmentId() { return departmentId; }
    public void setDepartmentId(int departmentId) { this.departmentId = departmentId; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }
    public String getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(String emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
    public String getProfileImageFileName() { return profileImageFileName; }
    public void setProfileImageFileName(String profileImageFileName) { this.profileImageFileName = profileImageFileName; }

    public String getFullName() {
        StringBuilder value = new StringBuilder();
        append(value, firstName); append(value, middleName); append(value, lastName); append(value, suffix);
        return value.toString();
    }
    private static void append(StringBuilder b, String v) {
        if (v != null && !v.isBlank()) {
            if (!b.isEmpty()) b.append(' ');
            b.append(v.trim().replaceAll("\\s+", " "));
        }
    }
}
