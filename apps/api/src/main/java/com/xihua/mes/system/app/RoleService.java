package com.xihua.mes.system.app;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xihua.mes.auth.CurrentContext;
import com.xihua.mes.auth.PermVersions;
import com.xihua.mes.auth.PermissionService;
import com.xihua.mes.auth.PermissionService.MenuGrant;
import com.xihua.mes.auth.UserAdminGuard;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.Errors;
import com.xihua.mes.common.error.FieldError;
import com.xihua.mes.common.web.Locks;
import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;
import com.xihua.mes.system.api.RoleDtos.RoleCreate;
import com.xihua.mes.system.api.RoleDtos.RoleMenus;
import com.xihua.mes.system.api.RoleDtos.RoleUpdate;
import com.xihua.mes.system.api.RoleDtos.RoleView;
import com.xihua.mes.system.domain.OrgRole;
import com.xihua.mes.system.domain.SysMenu;
import com.xihua.mes.system.infra.MenuMapper;
import com.xihua.mes.system.infra.RoleMapper;
import com.xihua.mes.system.infra.RoleMenuMapper;

@Service
public class RoleService {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    private static final Set<String> DATA_SCOPES = Set.of(
            "ALL", "PLANT", "DEPT_AND_CHILD", "DEPT", "SELF", "CUSTOM");
    private static final Map<String, String> SORT = Map.of(
            "code", "code", "name", "name", "sortNo", "sort_no", "createdAt", "created_at");

    private final RoleMapper roles;
    private final RoleMenuMapper roleMenus;
    private final MenuMapper menus;
    private final PermVersions versions;
    private final PermissionService permissions;
    private final UserAdminGuard guard;

    public RoleService(
            RoleMapper roles,
            RoleMenuMapper roleMenus,
            MenuMapper menus,
            PermVersions versions,
            PermissionService permissions,
            UserAdminGuard guard) {
        this.roles = roles;
        this.roleMenus = roleMenus;
        this.menus = menus;
        this.versions = versions;
        this.permissions = permissions;
        this.guard = guard;
    }

    public PageResult<RoleView> list(PageQuery page, String q) {
        QueryWrapper<OrgRole> qw = new QueryWrapper<>();
        PageQuery.fuzzy(qw, q, "code", "name");
        long total = roles.selectCount(qw);
        page.applyOrderAndLimit(qw, SORT, "sortNo");
        return new PageResult<>(roles.selectList(qw).stream().map(RoleView::of).toList(),
                page.page(), page.size(), total);
    }

    public RoleView get(UUID id) {
        return RoleView.of(require(id));
    }

    @Transactional
    public RoleView create(RoleCreate req) {
        requireBody(req);
        Errors errors = new Errors();
        String code = normalizeCode(req.code());
        if (!CODE.matcher(code).matches()) {
            errors.add("code", ErrorCodes.PATTERN_MISMATCH, "角色编码须为大写字母开头，仅含大写字母、数字或下划线");
        }
        String name = errors.text("name", req.name(), true, 64);
        String remark = errors.text("remark", blank(req.remark()), false, 500);
        String dataScope = dataScope(errors, req.dataScope(), "PLANT");
        errors.throwIfAny();
        if (roles.codeTaken(code) > 0) {
            throw ApiException.duplicateCode(code);
        }
        OrgRole row = new OrgRole();
        row.setId(UUID.randomUUID());
        row.setCode(code);
        row.setName(name);
        row.setRemark(remark);
        row.setDataScope(dataScope);
        row.setSortNo(req.sortNo() == null ? 0 : req.sortNo());
        row.setEnabled(req.enabled() == null || req.enabled());
        row.setBuiltin(false);
        row.setLockVersion(0);
        row.setDeleted(false);
        roles.insert(row);
        return get(row.getId());
    }

    @Transactional
    public RoleView update(UUID id, RoleUpdate req) {
        requireBody(req);
        OrgRole row = require(id);
        Locks.check(req.lockVersion(), row.getLockVersion());
        Errors errors = new Errors();
        String name = errors.text("name", req.name(), true, 64);
        String remark = errors.text("remark", blank(req.remark()), false, 500);
        String dataScope = dataScope(errors, req.dataScope(), row.getDataScope());
        errors.throwIfAny();
        boolean enabled = req.enabled() == null ? Boolean.TRUE.equals(row.getEnabled()) : req.enabled();
        if (Boolean.TRUE.equals(row.getBuiltin()) && !enabled) {
            throw ApiException.builtinProtected("内置角色不能停用");
        }
        boolean scopeChanged = !Objects.equals(row.getDataScope(), dataScope);
        boolean enabledChanged = Boolean.TRUE.equals(row.getEnabled()) != enabled;
        if ((scopeChanged || enabledChanged) && holds(id)) {
            throw ApiException.permissionDenied("sys:role:grant");
        }
        row.setName(name);
        row.setRemark(remark);
        row.setDataScope(dataScope);
        row.setSortNo(req.sortNo() == null ? row.getSortNo() : req.sortNo());
        row.setEnabled(enabled);
        Locks.requireUpdated(roles.updateById(row));
        if (scopeChanged || enabledChanged) {
            versions.bumpRole(id);
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id, Integer lockVersion) {
        OrgRole row = require(id);
        Locks.check(lockVersion, row.getLockVersion());
        if (Boolean.TRUE.equals(row.getBuiltin())) {
            throw ApiException.builtinProtected("内置角色不能删除");
        }
        if (roles.assignmentCount(id) > 0) {
            throw ApiException.inUse("角色已被用户引用");
        }
        roleMenus.deleteByRole(id);
        Locks.requireUpdated(roles.update(null, new UpdateWrapper<OrgRole>()
                .set("deleted", true).eq("id", id).eq("lock_version", lockVersion)));
    }

    public List<UUID> menusOf(UUID id) {
        require(id);
        return roleMenus.menuIds(id);
    }

    @Transactional
    public List<UUID> replaceMenus(UUID id, RoleMenus req) {
        require(id);
        requireBody(req);
        if (holds(id)) {
            throw ApiException.permissionDenied("sys:role:grant");
        }
        List<UUID> menuIds = req.menuIds() == null ? List.of() : req.menuIds().stream()
                .filter(Objects::nonNull).distinct().toList();
        List<SysMenu> selected = menuIds.isEmpty() ? List.of() : menus.selectByIds(menuIds);
        if (selected.size() != menuIds.size()) {
            throw ApiException.validation(List.of(
                    new FieldError("menuIds", ErrorCodes.INVALID_VALUE, "菜单不存在")));
        }
        permissions.requireMenusWithinCaller(selected.stream()
                .map(menu -> new MenuGrant(menu.getType(), menu.getPermCode()))
                .toList());
        roleMenus.deleteByRole(id);
        UUID actor = CurrentContext.userUuid();
        for (UUID menuId : menuIds) {
            roleMenus.insert(id, menuId, actor);
        }
        versions.bumpRole(id);
        return roleMenus.menuIds(id);
    }

    private boolean holds(UUID roleId) {
        if (guard.superAdmin()) {
            return false;
        }
        UUID personId = CurrentContext.personUuid();
        return personId != null && roles.activeHolder(personId, roleId) > 0;
    }

    private OrgRole require(UUID id) {
        OrgRole row = id == null ? null : roles.selectById(id);
        if (row == null) {
            throw ApiException.notFound("角色");
        }
        return row;
    }

    private static String dataScope(Errors errors, String raw, String fallback) {
        String value = blank(raw);
        if (value == null) {
            return fallback;
        }
        if (!DATA_SCOPES.contains(value)) {
            errors.add("dataScope", ErrorCodes.INVALID_VALUE,
                    "dataScope 只能是 ALL、PLANT、DEPT_AND_CHILD、DEPT、SELF 或 CUSTOM");
            return fallback;
        }
        return value;
    }

    private static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String blank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void requireBody(Object body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "请求体不是合法 JSON 或字段类型不符",
                    List.of());
        }
    }
}
