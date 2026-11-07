package com.xihua.mes.material.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.material.domain.Uom;

@Mapper
public interface UomMapper extends BaseMapper<Uom> {

    /** A global UoM is in use if any plant's data references it (bypasses the row filter on purpose). */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT EXISTS (SELECT 1 FROM mat_material WHERE uom_id = #{id} AND NOT deleted)
                OR EXISTS (SELECT 1 FROM mat_bom_line WHERE uom_id = #{id})
            """)
    boolean inUse(UUID id);
}
