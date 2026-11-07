package com.xihua.mes.org.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.org.domain.Plant;

@Mapper
public interface PlantMapper extends BaseMapper<Plant> {

    /** Cross-plant reference check before deleting a plant (bypasses the row filter on purpose). */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT EXISTS (SELECT 1 FROM mat_material WHERE plant_id = #{id} AND NOT deleted)
                OR EXISTS (SELECT 1 FROM mat_material_plant_ext WHERE plant_id = #{id})
                OR EXISTS (SELECT 1 FROM rtg_operation WHERE plant_id = #{id} AND NOT deleted)
                OR EXISTS (SELECT 1 FROM rtg_routing WHERE plant_id = #{id} AND NOT deleted)
                OR EXISTS (SELECT 1 FROM org_workshop WHERE plant_id = #{id} AND NOT deleted)
                OR EXISTS (SELECT 1 FROM cfg_custom_field_schema WHERE plant_id = #{id})
            """)
    boolean hasPlantData(UUID id);
}
