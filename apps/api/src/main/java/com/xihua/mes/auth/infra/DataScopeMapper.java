package com.xihua.mes.auth.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;

/** Reads the caller's data-scope inputs. Must not itself be filtered by data scope or plant. */
@Mapper
@InterceptorIgnore(dataPermission = "true", tenantLine = "true")
public interface DataScopeMapper {

    @Select("SELECT super_admin FROM sys_user WHERE id = #{id} AND NOT deleted")
    Boolean superAdmin(UUID id);

    @Select("""
            SELECT r.data_scope
              FROM org_person_plant pp
              JOIN org_person_plant_role pr ON pr.person_plant_id = pp.id
              JOIN org_role r ON r.id = pr.role_id AND NOT r.deleted AND r.enabled
             WHERE pp.person_id = #{personId}
               AND pp.plant_id = #{plantId}
               AND pp.active
            """)
    List<String> scopes(@Param("personId") UUID personId, @Param("plantId") UUID plantId);

    @Select("SELECT dept_id FROM org_person WHERE id = #{id} AND NOT deleted")
    UUID deptId(UUID id);

    @Select("SELECT ancestors FROM org_dept WHERE id = #{id} AND NOT deleted")
    String ancestors(UUID id);

    @Select("""
            SELECT DISTINCT rd.dept_id
              FROM org_person_plant pp
              JOIN org_person_plant_role pr ON pr.person_plant_id = pp.id
              JOIN org_role r ON r.id = pr.role_id AND NOT r.deleted AND r.enabled
                   AND r.data_scope = 'CUSTOM'
              JOIN sys_role_data_dept rd ON rd.role_id = r.id
             WHERE pp.person_id = #{personId}
               AND pp.plant_id = #{plantId}
               AND pp.active
            """)
    List<UUID> customDeptIds(@Param("personId") UUID personId, @Param("plantId") UUID plantId);

    @Select("""
            SELECT plant_id FROM org_person_plant
             WHERE person_id = #{personId} AND active
            """)
    List<UUID> assignedPlantIds(UUID personId);
}
