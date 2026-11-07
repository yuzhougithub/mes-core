package com.xihua.mes.material.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.material.domain.Material;

@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    /** Material codes are globally unique across shared and all plants' private rows (A5). */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT EXISTS (SELECT 1 FROM mat_material WHERE code = #{code} AND NOT deleted)")
    boolean codeTaken(String code);

    /** Referenced anywhere (any plant): BOM header / line, routing. */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT EXISTS (SELECT 1 FROM mat_bom WHERE material_id = #{id} AND NOT deleted)
                OR EXISTS (SELECT 1 FROM mat_bom_line WHERE component_material_id = #{id})
                OR EXISTS (SELECT 1 FROM rtg_routing WHERE material_id = #{id} AND NOT deleted)
            """)
    boolean inUse(UUID id);

    @Select("SELECT EXISTS (SELECT 1 FROM mat_category WHERE id = #{id} AND NOT deleted)")
    boolean categoryExists(UUID id);
}
