package com.xihua.mes.auth.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.auth.domain.RefreshToken;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    @Select("SELECT * FROM auth_refresh_token WHERE token_hash = #{hash} FOR UPDATE")
    RefreshToken lockByHash(String hash);

    @Update("UPDATE auth_refresh_token SET revoked_at = #{now} WHERE id = #{id} AND revoked_at IS NULL")
    int revokeOne(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE auth_refresh_token SET revoked_at = #{now}
             WHERE family_id = #{familyId} AND revoked_at IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE auth_refresh_token SET revoked_at = #{now}
             WHERE user_id = #{userId} AND revoked_at IS NULL
            """)
    int revokeAll(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    @Update("UPDATE auth_refresh_token SET plant_id = #{plantId} WHERE id = #{id} AND revoked_at IS NULL")
    int updatePlant(@Param("id") UUID id, @Param("plantId") UUID plantId);

    @Delete("DELETE FROM auth_refresh_token WHERE user_id = #{userId}")
    int deleteByUser(UUID userId);
}
