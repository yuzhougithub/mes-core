package com.xihua.mes.routing.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.routing.domain.Routing;

@Mapper
public interface RoutingMapper extends BaseMapper<Routing> {

    /** {@code (code, plant_id, version_no)} unique, NULLS NOT DISTINCT (mirrors uk_rtg_routing_version). */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT EXISTS (SELECT 1 FROM rtg_routing
                           WHERE code = #{code} AND plant_id IS NOT DISTINCT FROM #{plantId}::uuid
                             AND version_no = #{versionNo} AND NOT deleted)
            """)
    boolean versionTaken(@Param("code") String code, @Param("plantId") UUID plantId,
            @Param("versionNo") String versionNo);
}
