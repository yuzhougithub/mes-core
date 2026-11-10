package com.xihua.mes.org.infra;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;

/**
 * Person-plant bindings. Queries ignore the plant row filter: account administration and login
 * need every assignment, not only the caller's current plant.
 */
@Mapper
public interface PersonPlantMapper {

    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM org_person_plant WHERE person_id = #{personId}")
    int deleteByPerson(UUID personId);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO org_person_plant
                (id, person_id, plant_id, workshop_id, job_title, active, is_default, ext_values,
                 lock_version, created_by, updated_by)
            VALUES
                (#{id}, #{personId}, #{plantId}, #{workshopId}, #{jobTitle}, #{active}, #{defaultPlant},
                 CAST('{}' AS jsonb), 0, #{createdBy}, #{createdBy})
            """)
    int insertAssignment(
            @Param("id") UUID id,
            @Param("personId") UUID personId,
            @Param("plantId") UUID plantId,
            @Param("workshopId") UUID workshopId,
            @Param("jobTitle") String jobTitle,
            @Param("active") boolean active,
            @Param("defaultPlant") boolean defaultPlant,
            @Param("createdBy") UUID createdBy);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO org_person_plant_role (person_plant_id, role_id, created_by)
            VALUES (#{personPlantId}, #{roleId}, #{createdBy})
            """)
    int insertRole(
            @Param("personPlantId") UUID personPlantId,
            @Param("roleId") UUID roleId,
            @Param("createdBy") UUID createdBy);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT pp.id, pp.plant_id, pl.code AS plant_code, pl.name AS plant_name,
                   pp.is_default AS default_plant, pp.active, pp.workshop_id, pp.job_title, pp.lock_version
              FROM org_person_plant pp
              JOIN org_plant pl ON pl.id = pp.plant_id
             WHERE pp.person_id = #{personId}
             ORDER BY pl.code, pp.plant_id
            """)
    List<AssignmentRow> listByPerson(UUID personId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT role_id FROM org_person_plant_role
             WHERE person_plant_id = #{personPlantId}
             ORDER BY role_id
            """)
    List<UUID> roleIds(UUID personPlantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT pp.plant_id, pl.code AS plant_code, pl.name AS plant_name, pp.is_default AS default_plant
              FROM org_person_plant pp
              JOIN org_plant pl ON pl.id = pp.plant_id AND pl.enabled AND NOT pl.deleted
             WHERE pp.person_id = #{personId} AND pp.active
             ORDER BY pl.code, pp.plant_id
            """)
    List<ActivePlantRow> activePlants(UUID personId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT role.code
              FROM org_person_plant pp
              JOIN org_person_plant_role pr ON pr.person_plant_id = pp.id
              JOIN org_role role ON role.id = pr.role_id AND NOT role.deleted
             WHERE pp.person_id = #{personId} AND pp.plant_id = #{plantId} AND pp.active
             ORDER BY role.code
            """)
    List<String> roleCodes(@Param("personId") UUID personId, @Param("plantId") UUID plantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM org_workshop
                 WHERE id = #{id} AND plant_id = #{plantId} AND enabled AND NOT deleted)
            """)
    boolean workshopEnabledInPlant(@Param("id") UUID id, @Param("plantId") UUID plantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT id FROM org_role
             WHERE NOT deleted AND id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<UUID> existingRoleIds(@Param("ids") Collection<UUID> ids);
}
