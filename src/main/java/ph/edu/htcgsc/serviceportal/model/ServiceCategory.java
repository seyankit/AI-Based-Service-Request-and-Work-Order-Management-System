package ph.edu.htcgsc.serviceportal.model;

public class ServiceCategory {

    private int categoryId;
    private String categoryCode;
    private String categoryName;
    private String categoryDescription;
    private int defaultDepartmentId;
    private String defaultDepartmentName;
    private int displayOrder;
    private boolean active;

    public ServiceCategory() {
    }

    public ServiceCategory(
            int categoryId,
            String categoryCode,
            String categoryName,
            String categoryDescription,
            int defaultDepartmentId,
            String defaultDepartmentName,
            int displayOrder,
            boolean active
    ) {
        this.categoryId = categoryId;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.categoryDescription = categoryDescription;
        this.defaultDepartmentId = defaultDepartmentId;
        this.defaultDepartmentName = defaultDepartmentName;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryDescription() {
        return categoryDescription;
    }

    public void setCategoryDescription(String categoryDescription) {
        this.categoryDescription = categoryDescription;
    }

    public int getDefaultDepartmentId() {
        return defaultDepartmentId;
    }

    public void setDefaultDepartmentId(int defaultDepartmentId) {
        this.defaultDepartmentId = defaultDepartmentId;
    }

    public String getDefaultDepartmentName() {
        return defaultDepartmentName;
    }

    public void setDefaultDepartmentName(String defaultDepartmentName) {
        this.defaultDepartmentName = defaultDepartmentName;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}