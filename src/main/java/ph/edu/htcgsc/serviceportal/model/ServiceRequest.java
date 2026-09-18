package ph.edu.htcgsc.serviceportal.model;

public class ServiceRequest {

    private long requestId;
    private String requestNumber;

    private int requesterId;
    private int requesterDepartmentId;
    private String requesterDepartmentName;

    private Integer requestedCategoryId;
    private String requestedCategoryName;
    private Integer finalCategoryId;
    private String finalCategoryName;
    private Integer routedDepartmentId;

    private String preferredPriority;
    private String finalPriority;

    private String title;
    private String description;
    private String location;

    /*
     * Dates and timestamps use ISO-formatted strings in JSON:
     *
     * Date:
     * 2026-09-14
     *
     * Timestamp:
     * 2026-09-14T21:15:30
     */
    private String dateReported;
    private String currentStatus;
    private String completedAt;
    private String closedAt;
    private String createdAt;
    private String updatedAt;

    public ServiceRequest() {
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public void setRequestNumber(String requestNumber) {
        this.requestNumber = requestNumber;
    }

    public int getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(int requesterId) {
        this.requesterId = requesterId;
    }

    public int getRequesterDepartmentId() {
        return requesterDepartmentId;
    }

    public void setRequesterDepartmentId(
            int requesterDepartmentId
    ) {
        this.requesterDepartmentId =
                requesterDepartmentId;
    }

    public String getRequesterDepartmentName() {
        return requesterDepartmentName;
    }

    public void setRequesterDepartmentName(
            String requesterDepartmentName
    ) {
        this.requesterDepartmentName =
                requesterDepartmentName;
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

    public String getRequestedCategoryName() {
        return requestedCategoryName;
    }

    public void setRequestedCategoryName(
            String requestedCategoryName
    ) {
        this.requestedCategoryName =
                requestedCategoryName;
    }
    public Integer getFinalCategoryId() {
        return finalCategoryId;
    }

    public void setFinalCategoryId(
            Integer finalCategoryId
    ) {
        this.finalCategoryId =
                finalCategoryId;
    }

    public String getFinalCategoryName() {
        return finalCategoryName;
    }

    public void setFinalCategoryName(
            String finalCategoryName
    ) {
        this.finalCategoryName =
                finalCategoryName;
    }
    public Integer getRoutedDepartmentId() {
        return routedDepartmentId;
    }

    public void setRoutedDepartmentId(
            Integer routedDepartmentId
    ) {
        this.routedDepartmentId =
                routedDepartmentId;
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

    public String getFinalPriority() {
        return finalPriority;
    }

    public void setFinalPriority(
            String finalPriority
    ) {
        this.finalPriority =
                finalPriority;
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

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(
            String currentStatus
    ) {
        this.currentStatus = currentStatus;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(
            String completedAt
    ) {
        this.completedAt = completedAt;
    }

    public String getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(
            String closedAt
    ) {
        this.closedAt = closedAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            String createdAt
    ) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(
            String updatedAt
    ) {
        this.updatedAt = updatedAt;
    }
}