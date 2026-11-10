package com.xihua.mes.org.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** Rows for {@code GET /org/tree}. Plant filtering is the interceptor's job. */
@Mapper
public interface OrgTreeMapper {

    @Select("""
            SELECT id, code, name
              FROM org_plant
             WHERE NOT deleted
             ORDER BY code, id
            """)
    List<PlantRow> plants();

    @Select("""
            SELECT id, plant_id, code, name
              FROM org_workshop
             WHERE NOT deleted
             ORDER BY code, id
            """)
    List<WorkshopRow> workshops();

    @Select("""
            SELECT id, plant_id, workshop_id, code, name
              FROM org_work_center
             WHERE NOT deleted AND work_center_type = 'LINE'
             ORDER BY code, id
            """)
    List<LineRow> lines();

    class PlantRow {
        private UUID id;
        private String code;
        private String name;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    class WorkshopRow {
        private UUID id;
        private UUID plantId;
        private String code;
        private String name;

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

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    class LineRow {
        private UUID id;
        private UUID plantId;
        private UUID workshopId;
        private String code;
        private String name;

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

        public UUID getWorkshopId() {
            return workshopId;
        }

        public void setWorkshopId(UUID workshopId) {
            this.workshopId = workshopId;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
