package com.xihua.mes.system.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleDataDeptMapper {

    @Select("""
            SELECT dept_id FROM sys_role_data_dept
             WHERE role_id = #{roleId}
             ORDER BY dept_id
            """)
    List<UUID> deptIds(UUID roleId);

    @Delete("DELETE FROM sys_role_data_dept WHERE role_id = #{roleId}")
    int deleteByRole(UUID roleId);

    @Insert("""
            INSERT INTO sys_role_data_dept (role_id, dept_id, created_by)
            VALUES (#{roleId}, #{deptId}, #{actor})
            """)
    int insert(@Param("roleId") UUID roleId, @Param("deptId") UUID deptId, @Param("actor") UUID actor);
}
