package ph.edu.htcgsc.serviceportal.model;
public class ServiceRequestDraft {
    private long draftId; private Integer requestedCategoryId; private String preferredPriority,title,description,location,dateReported,createdAt,updatedAt;
    public long getDraftId(){return draftId;} public void setDraftId(long v){draftId=v;}
    public Integer getRequestedCategoryId(){return requestedCategoryId;} public void setRequestedCategoryId(Integer v){requestedCategoryId=v;}
    public String getPreferredPriority(){return preferredPriority;} public void setPreferredPriority(String v){preferredPriority=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getLocation(){return location;} public void setLocation(String v){location=v;}
    public String getDateReported(){return dateReported;} public void setDateReported(String v){dateReported=v;}
    public String getCreatedAt(){return createdAt;} public void setCreatedAt(String v){createdAt=v;}
    public String getUpdatedAt(){return updatedAt;} public void setUpdatedAt(String v){updatedAt=v;}
}
