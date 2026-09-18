package ph.edu.htcgsc.serviceportal.dto;

/*
 * Contains only fields that a requester is allowed to submit.
 *
 * The browser must not submit:
 * - requesterId
 * - requesterDepartmentId
 * - finalCategoryId
 * - finalPriority
 * - routedDepartmentId
 * - currentStatus
 *
 * Those values will be obtained or controlled by the Java backend.
 */
public class CreateServiceRequestRequest {

    /*
     * Nullable when the requester selects:
     * "Let the system recommend a category."
     */
    private Integer requestedCategoryId;

    private String preferredPriority;
    private String title;
    private String description;
    private String location;

    /*
     * Expected JSON format: YYYY-MM-DD
     */
    private String dateReported;

    public CreateServiceRequestRequest() {
    }

    public Integer getRequestedCategoryId() {
        return requestedCategoryId;
    }

    public void setRequestedCategoryId(
            Integer requestedCategoryId
    ) {
        this.requestedCategoryId =
                requestedCategoryId;
    }

    public String getPreferredPriority() {
        return preferredPriority;
    }

    public void setPreferredPriority(
            String preferredPriority
    ) {
        this.preferredPriority =
                preferredPriority;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(
            String description
    ) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDateReported() {
        return dateReported;
    }

    public void setDateReported(
            String dateReported
    ) {
        this.dateReported = dateReported;
    }
}