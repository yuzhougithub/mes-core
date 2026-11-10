package com.xihua.mes.org.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.org.domain.Dept;

@Mapper
public interface DeptMapper extends BaseMapper<Dept> {

    @Select("""
            SELECT count(*) FROM org_dept
             WHERE NOT deleted AND parent_id = #{id}
            """)
    int childCount(UUID id);

    @Select("""
            SELECT count(*) FROM org_person
             WHERE NOT deleted AND dept_id = #{id}
            """)
    int personCount(UUID id);

    /** Visible in any plant. Parent checks and CUSTOM dept ids must see rows outside the current plant. */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM org_dept WHERE id = #{id} AND NOT deleted")
    Dept findAny(UUID id);

    @Update("""
            UPDATE org_dept
               SET ancestors = #{newPrefix} || substr(ancestors, char_length(#{oldPrefix}) + 1),
                   updated_at = now(),
                   updated_by = #{actor}
             WHERE NOT deleted
               AND id <> #{selfId}
               AND ancestors LIKE #{oldPrefix} || '%'
            """)
    int rewriteDescendants(
            @Param("selfId") UUID selfId,
            @Param("oldPrefix") String oldPrefix,
            @Param("newPrefix") String newPrefix,
            @Param("actor") UUID actor);
}
