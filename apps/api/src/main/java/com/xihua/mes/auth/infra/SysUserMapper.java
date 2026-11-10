package com.xihua.mes.auth.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.auth.domain.SysUser;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND NOT deleted")
    SysUser findByUsername(String username);

    @Update("""
            UPDATE sys_user
               SET fail_count = 0, locked_until = NULL, updated_at = #{now}
             WHERE id = #{id} AND locked_until IS NOT NULL AND locked_until <= #{now} AND NOT deleted
            """)
    int clearExpiredLock(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE sys_user
               SET fail_count = fail_count + 1,
                   locked_until = CASE
                       WHEN fail_count + 1 >= #{maxFailures} THEN #{lockedUntil}
                       ELSE locked_until
                   END,
                   updated_at = #{now}
             WHERE id = #{id} AND NOT deleted
            """)
    int registerFailure(
            @Param("id") UUID id,
            @Param("maxFailures") int maxFailures,
            @Param("lockedUntil") OffsetDateTime lockedUntil,
            @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE sys_user
               SET fail_count = 0,
                   locked_until = NULL,
                   last_login_at = #{now},
                   last_login_ip = #{ip},
                   last_plant_id = #{plantId},
                   updated_at = #{now}
             WHERE id = #{id} AND NOT deleted
            """)
    int markLogin(
            @Param("id") UUID id,
            @Param("plantId") UUID plantId,
            @Param("ip") String ip,
            @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE sys_user
               SET last_plant_id = #{plantId}, updated_at = #{now}
             WHERE id = #{id} AND NOT deleted
            """)
    int touchLastPlant(@Param("id") UUID id, @Param("plantId") UUID plantId, @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE sys_user
               SET password_hash = #{passwordHash},
                   must_change_pwd = #{mustChangePwd},
                   pwd_changed_at = #{now},
                   fail_count = 0,
                   locked_until = NULL,
                   session_invalid_before = #{now},
                   lock_version = lock_version + 1,
                   updated_at = #{now}
             WHERE id = #{id} AND NOT deleted
            """)
    int changePassword(
            @Param("id") UUID id,
            @Param("passwordHash") String passwordHash,
            @Param("mustChangePwd") boolean mustChangePwd,
            @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE sys_user
               SET status = #{status},
                   session_invalid_before = CASE WHEN #{status} = 'DISABLED' THEN #{now} ELSE session_invalid_before END,
                   lock_version = lock_version + 1,
                   updated_at = #{now}
             WHERE id = #{id} AND lock_version = #{lockVersion} AND NOT deleted
            """)
    int updateStatus(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("lockVersion") int lockVersion,
            @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE sys_user
               SET fail_count = 0,
                   locked_until = NULL,
                   lock_version = lock_version + 1,
                   updated_at = #{now}
             WHERE id = #{id} AND lock_version = #{lockVersion} AND NOT deleted
            """)
    int unlock(@Param("id") UUID id, @Param("lockVersion") int lockVersion, @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE sys_user
               SET username = #{username},
                   lock_version = lock_version + 1,
                   updated_at = #{now}
             WHERE id = #{id} AND lock_version = #{lockVersion} AND NOT deleted
            """)
    int updateUsername(
            @Param("id") UUID id,
            @Param("username") String username,
            @Param("lockVersion") int lockVersion,
            @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE sys_user
               SET default_plant_id = #{plantId},
                   perm_version = perm_version + 1,
                   lock_version = lock_version + 1,
                   updated_at = #{now}
             WHERE id = #{id} AND lock_version = #{lockVersion} AND NOT deleted
            """)
    int replaceDefaultPlant(
            @Param("id") UUID id,
            @Param("plantId") UUID plantId,
            @Param("lockVersion") int lockVersion,
            @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE sys_user
               SET session_invalid_before = #{now}, updated_at = #{now}
             WHERE id = #{id} AND NOT deleted
            """)
    int invalidateSessions(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    @Select("""
            SELECT count(*) FROM sys_user
             WHERE super_admin AND status = 'ACTIVE' AND NOT deleted AND id <> #{id}
            """)
    int otherActiveSuperAdmins(UUID id);
}
