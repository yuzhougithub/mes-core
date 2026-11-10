package com.xihua.mes.system.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.system.domain.OrgRole;

@Mapper
public interface RoleMapper extends BaseMapper<OrgRole> {

    @Select("""
            SELECT count(*) FROM org_role
             WHERE NOT deleted AND code = #{code}
            """)
    int codeTaken(String code);

    @Select("""
            SELECT count(*) FROM org_person_plant_role
             WHERE role_id = #{roleId}
            """)
    int assignmentCount(UUID roleId);

    @Select("""
            SELECT count(*)
              FROM org_person_plant pp
              JOIN org_person_plant_role pr ON pr.person_plant_id = pp.id
             WHERE pp.person_id = #{personId}
               AND pp.active
               AND pr.role_id = #{roleId}
            """)
    int activeHolder(@Param("personId") UUID personId, @Param("roleId") UUID roleId);
}
