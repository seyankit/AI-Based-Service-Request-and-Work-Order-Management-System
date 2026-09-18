package ph.edu.htcgsc.serviceportal.model;

public final class AccountReview {

    private final long accountReviewId;
    private final int personnelId;

    private final String firstName;
    private final String lastName;
    private final String email;
    private final String contactNumber;
    private final String personnelType;

    private final int requestedRoleId;
    private final String requestedRoleName;

    private final int requestedDepartmentId;
    private final String requestedDepartmentName;

    private final String reviewStatus;
    private final Integer reviewedById;
    private final String decisionRemarks;

    private final String submittedAt;
    private final String reviewedAt;
    private final String updatedAt;

    public AccountReview(
            long accountReviewId,
            int personnelId,
            String firstName,
            String lastName,
            String email,
            String contactNumber,
            String personnelType,
            int requestedRoleId,
            String requestedRoleName,
            int requestedDepartmentId,
            String requestedDepartmentName,
            String reviewStatus,
            Integer reviewedById,
            String decisionRemarks,
            String submittedAt,
            String reviewedAt,
            String updatedAt
    ) {
        this.accountReviewId =
                accountReviewId;

        this.personnelId =
                personnelId;

        this.firstName =
                firstName;

        this.lastName =
                lastName;

        this.email =
                email;

        this.contactNumber =
                contactNumber;

        this.personnelType =
                personnelType;

        this.requestedRoleId =
                requestedRoleId;

        this.requestedRoleName =
                requestedRoleName;

        this.requestedDepartmentId =
                requestedDepartmentId;

        this.requestedDepartmentName =
                requestedDepartmentName;

        this.reviewStatus =
                reviewStatus;

        this.reviewedById =
                reviewedById;

        this.decisionRemarks =
                decisionRemarks;

        this.submittedAt =
                submittedAt;

        this.reviewedAt =
                reviewedAt;

        this.updatedAt =
                updatedAt;
    }

    public long getAccountReviewId() {
        return accountReviewId;
    }

    public int getPersonnelId() {
        return personnelId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public String getPersonnelType() {
        return personnelType;
    }

    public int getRequestedRoleId() {
        return requestedRoleId;
    }

    public String getRequestedRoleName() {
        return requestedRoleName;
    }

    public int getRequestedDepartmentId() {
        return requestedDepartmentId;
    }

    public String getRequestedDepartmentName() {
        return requestedDepartmentName;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public Integer getReviewedById() {
        return reviewedById;
    }

    public String getDecisionRemarks() {
        return decisionRemarks;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public String getReviewedAt() {
        return reviewedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}