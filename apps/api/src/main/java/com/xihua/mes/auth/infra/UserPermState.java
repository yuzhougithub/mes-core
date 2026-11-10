package com.xihua.mes.auth.infra;

/** {@code super_admin} and {@code perm_version} loaded together for one authorization check. */
public class UserPermState {

    private Boolean superAdmin;
    private Integer permVersion;

    public Boolean getSuperAdmin() {
        return superAdmin;
    }

    public void setSuperAdmin(Boolean superAdmin) {
        this.superAdmin = superAdmin;
    }

    public Integer getPermVersion() {
        return permVersion;
    }

    public void setPermVersion(Integer permVersion) {
        this.permVersion = permVersion;
    }
}
