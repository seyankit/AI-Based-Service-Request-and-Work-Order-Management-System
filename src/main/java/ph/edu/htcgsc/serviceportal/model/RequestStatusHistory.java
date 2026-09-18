package ph.edu.htcgsc.serviceportal.model;

public class RequestStatusHistory {

    private long historyId;
    private long requestId;

    private String previousStatus;
    private String newStatus;

    private int changedBy;
    private int changedByRoleId;

    private String changeReason;
    private String changedAt;

    public RequestStatusHistory() {
    }

    public long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(long historyId) {
        this.historyId = historyId;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(
            String previousStatus
    ) {
        this.previousStatus = previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(
            String newStatus
    ) {
        this.newStatus = newStatus;
    }

    public int getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(int changedBy) {
        this.changedBy = changedBy;
    }

    public int getChangedByRoleId() {
        return changedByRoleId;
    }

    public void setChangedByRoleId(
            int changedByRoleId
    ) {
        this.changedByRoleId = changedByRoleId;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(
            String changeReason
    ) {
        this.changeReason = changeReason;
    }

    public String getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(
            String changedAt
    ) {
        this.changedAt = changedAt;
    }
}