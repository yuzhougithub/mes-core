package com.xihua.mes.auth.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RevokedJtiMapper {

    @Insert("""
            INSERT INTO auth_revoked_jti (jti, user_id, expires_at)
            VALUES (#{jti}, #{userId}, #{expiresAt})
            ON CONFLICT (jti) DO NOTHING
            """)
    int insertIgnore(
            @Param("jti") UUID jti,
            @Param("userId") UUID userId,
            @Param("expiresAt") OffsetDateTime expiresAt);

    @Select("SELECT EXISTS (SELECT 1 FROM auth_revoked_jti WHERE jti = #{jti})")
    boolean exists(UUID jti);
}
