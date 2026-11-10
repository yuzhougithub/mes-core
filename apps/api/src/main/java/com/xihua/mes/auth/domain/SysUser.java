package com.xihua.mes.auth.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

/** Account. Exactly one row per {@code org_person}. Password hash is the credential. */
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String username;
    private UUID personId;
    private String passwordHash;
    private String status;
    private Boolean superAdmin;
    private Boolean mustChangePwd;
    private OffsetDateTime pwdChangedAt;
    private Integer failCount;
    private OffsetDateTime lockedUntil;
    private UUID defaultPlantId;
    private UUID lastPlantId;
    private OffsetDateTime lastLoginAt;
    private String lastLoginIp;
    private Integer permVersion;
    private OffsetDateTime sessionInvalidBefore;

    @Version
    private Integer lockVersion;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT)
    private UUID createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private UUID updatedBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UUID getPersonId() {
        return personId;
    }

    public void setPersonId(UUID personId) {
        this.personId = personId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getSuperAdmin() {
        return superAdmin;
    }

    public void setSuperAdmin(Boolean superAdmin) {
        this.superAdmin = superAdmin;
    }

    public Boolean getMustChangePwd() {
        return mustChangePwd;
    }

    public void setMustChangePwd(Boolean mustChangePwd) {
        this.mustChangePwd = mustChangePwd;
    }

    public OffsetDateTime getPwdChangedAt() {
        return pwdChangedAt;
    }

    public void setPwdChangedAt(OffsetDateTime pwdChangedAt) {
        this.pwdChangedAt = pwdChangedAt;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(OffsetDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public UUID getDefaultPlantId() {
        return defaultPlantId;
    }

    public void setDefaultPlantId(UUID defaultPlantId) {
        this.defaultPlantId = defaultPlantId;
    }

    public UUID getLastPlantId() {
        return lastPlantId;
    }

    public void setLastPlantId(UUID lastPlantId) {
        this.lastPlantId = lastPlantId;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(OffsetDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public Integer getPermVersion() {
        return permVersion;
    }

    public void setPermVersion(Integer permVersion) {
        this.permVersion = permVersion;
    }

    public OffsetDateTime getSessionInvalidBefore() {
        return sessionInvalidBefore;
    }

    public void setSessionInvalidBefore(OffsetDateTime sessionInvalidBefore) {
        this.sessionInvalidBefore = sessionInvalidBefore;
    }

    public Integer getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Integer lockVersion) {
        this.lockVersion = lockVersion;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
