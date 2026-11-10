package com.xihua.mes.org.infra;

import java.util.UUID;

/** An active assignment at an enabled plant. */
public class ActivePlantRow {

    private UUID plantId;
    private String plantCode;
    private String plantName;
    private Boolean defaultPlant;

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
}
