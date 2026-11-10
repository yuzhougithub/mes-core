package com.xihua.mes.org.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PersonPostMapper {

    @Select("""
            SELECT post_id FROM org_person_post
             WHERE person_id = #{personId}
             ORDER BY post_id
            """)
    List<UUID> postIds(UUID personId);

    @Delete("DELETE FROM org_person_post WHERE person_id = #{personId}")
    int deleteByPerson(UUID personId);

    @Insert("""
            INSERT INTO org_person_post (person_id, post_id, created_by)
            VALUES (#{personId}, #{postId}, #{actor})
            """)
    int insert(@Param("personId") UUID personId, @Param("postId") UUID postId, @Param("actor") UUID actor);
}
