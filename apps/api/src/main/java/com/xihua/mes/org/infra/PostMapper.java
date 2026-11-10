package com.xihua.mes.org.infra;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.org.domain.Post;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Select("""
            SELECT count(*) FROM org_person_post
             WHERE post_id = #{id}
            """)
    int personCount(UUID id);

    @Select("""
            <script>
            SELECT id FROM org_post
             WHERE NOT deleted AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<UUID> existingIds(@Param("ids") Collection<UUID> ids);
}
