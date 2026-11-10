package com.xihua.mes.org.infra;

import java.util.UUID;

/** Row of {@code org_person_plant} joined to the plant, for account administration. */
public class AssignmentRow {

    private UUID id;
    private UUID plantId;
    private String plantCode;
    private String plantName;
    private Boolean defaultPlant;
    private Boolean active;
    private UUID workshopId;
    private String jobTitle;
    private Integer lockVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPlantId() {
        return plantId;
    }

    public void setPlantId(UUID plantId) {
        this.plantId = plantId;
    }

    public String getPlantCode() {
        return plantCode;
    }

    public void setPlantCode(String plantCode) {
        this.plantCode = plantCode;
    }

    public String getPlantName() {
        return plantName;
    }

    public void setPlantName(String plantName) {
        this.plantName = plantName;
    }

    public Boolean getDefaultPlant() {
        return defaultPlant;
    }

    public void setDefaultPlant(Boolean defaultPlant) {
        this.defaultPlant = defaultPlant;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public UUID getWorkshopId() {
        return workshopId;
    }

    public void setWorkshopId(UUID workshopId) {
        this.workshopId = workshopId;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public Integer getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Integer lockVersion) {
        this.lockVersion = lockVersion;
    }
}
