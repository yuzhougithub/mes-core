package com.xihua.mes.system.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xihua.mes.auth.PermVersions;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.Errors;
import com.xihua.mes.common.web.Locks;
import com.xihua.mes.system.api.MenuDtos.MenuNode;
import com.xihua.mes.system.api.MenuDtos.MenuWrite;
import com.xihua.mes.system.domain.SysMenu;
import com.xihua.mes.system.infra.MenuMapper;

@Service
public class MenuService {

    private static final Pattern PERM = Pattern.compile("^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*){1,3}$");
    private static final Set<String> TYPES = Set.of("DIR", "MENU", "BUTTON");
    private static final UUID NIL = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final MenuMapper menus;
    private final PermVersions versions;

    public MenuService(MenuMapper menus, PermVersions versions) {
        this.menus = menus;
        this.versions = versions;
    }

    public List<MenuNode> tree() {
        return build(menus.listAll());
    }

    public MenuNode get(UUID id) {
        return MenuNode.of(require(id), List.of());
    }

    @Transactional
    public MenuNode create(MenuWrite req) {
        requireBody(req);
        Draft draft = draft(req, null, true);
        validate(draft, null);
        SysMenu row = new SysMenu();
        row.setId(UUID.randomUUID());
        apply(row, draft);
        row.setLockVersion(0);
        row.setDeleted(false);
        insert(row);
        return get(row.getId());
    }

    @Transactional
    public MenuNode update(UUID id, MenuWrite req) {
        requireBody(req);
        SysMenu row = require(id);
        Locks.check(req.lockVersion(), row.getLockVersion());
        Draft draft = draft(req, row, false);
        validate(draft, id);
        boolean permChanged = !Objects.equals(row.getPermCode(), draft.permCode());
        boolean enabledChanged = !Objects.equals(Boolean.TRUE.equals(row.getEnabled()), draft.enabled());
        apply(row, draft);
        try {
            Locks.requireUpdated(menus.updateById(row));
        } catch (DuplicateKeyException ex) {
            throw duplicate(ex, draft);
        }
        if (permChanged || enabledChanged) {
            versions.bumpMenu(id);
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id, Integer lockVersion) {
        SysMenu row = require(id);
        Locks.check(lockVersion, row.getLockVersion());
        if (menus.childCount(id) > 0) {
            throw ApiException.menuHasChildren();
        }
        List<String> roles = menus.referencingRoleCodes(id);
        if (!roles.isEmpty()) {
            throw ApiException.menuInUse(roles);
        }
        Locks.requireUpdated(menus.update(null, new UpdateWrapper<SysMenu>()
                .set("deleted", true).eq("id", id).eq("lock_version", lockVersion)));
    }

    private void insert(SysMenu row) {
        try {
            menus.insert(row);
        } catch (DuplicateKeyException ex) {
            throw duplicate(ex, new Draft(row.getType(), row.getParentId(), row.getName(), row.getPermCode(),
                    row.getPath(), row.getComponent(), row.getIcon(),
                    row.getSortNo() == null ? 0 : row.getSortNo(),
                    Boolean.TRUE.equals(row.getVisible()), Boolean.TRUE.equals(row.getEnabled())));
        }
    }

    private void validate(Draft draft, UUID selfId) {
        assertParent(draft.type(), draft.parentId(), selfId);
        assertChildrenFit(selfId, draft.type());
        if (draft.permCode() != null && menus.permCodeTaken(draft.permCode(), selfId == null ? NIL : selfId) > 0) {
            throw ApiException.duplicatePermCode(draft.permCode());
        }
        if ("MENU".equals(draft.type())
                && menus.siblingPathTaken(draft.path(), draft.parentId(), selfId == null ? NIL : selfId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_CODE, "同级路由已存在：" + draft.path(),
                    List.of(new com.xihua.mes.common.error.FieldError(
                            "path", ErrorCodes.DUPLICATE_CODE, "同级路由已存在：" + draft.path())));
        }
    }

    private void assertParent(String type, UUID parentId, UUID selfId) {
        if (selfId != null && selfId.equals(parentId)) {
            throw ApiException.menuParentInvalid("不能把节点移到自身下面");
        }
        if (selfId != null && parentId != null && isUnder(parentId, selfId)) {
            throw ApiException.menuParentInvalid("不能把节点移到自己的子孙下面");
        }
        SysMenu parent = null;
        if (parentId != null) {
            parent = menus.selectById(parentId);
            if (parent == null) {
                throw ApiException.menuParentInvalid("父节点不存在");
            }
        }
        boolean ok = switch (type) {
            case "DIR", "MENU" -> parent == null || "DIR".equals(parent.getType());
            case "BUTTON" -> parent != null && "MENU".equals(parent.getType());
            default -> false;
        };
        if (!ok) {
            throw ApiException.menuParentInvalid(parentMessage(type));
        }
    }

    private void assertChildrenFit(UUID selfId, String newType) {
        if (selfId == null) {
            return;
        }
        for (SysMenu row : menus.listAll()) {
            if (!selfId.equals(row.getParentId())) {
                continue;
            }
            boolean ok = switch (row.getType()) {
                case "DIR", "MENU" -> "DIR".equals(newType);
                case "BUTTON" -> "MENU".equals(newType);
                default -> false;
            };
            if (!ok) {
                throw ApiException.menuParentInvalid("子节点与新的类型不匹配");
            }
        }
    }

    private boolean isUnder(UUID nodeId, UUID ancestorId) {
        Set<UUID> seen = new HashSet<>();
        UUID current = nodeId;
        while (current != null && seen.add(current)) {
            if (current.equals(ancestorId)) {
                return true;
            }
            SysMenu row = menus.selectById(current);
            current = row == null ? null : row.getParentId();
        }
        return false;
    }

    private Draft draft(MenuWrite req, SysMenu existing, boolean create) {
        Errors errors = new Errors();
        String type = blank(req.type());
        if (type == null || !TYPES.contains(type)) {
            errors.add("type", ErrorCodes.INVALID_VALUE, "type 只能是 DIR、MENU 或 BUTTON");
        }
        String name = errors.text("name", req.name(), true, 64);
        String perm = blank(req.permCode());
        String path = blank(req.path());
        String component = blank(req.component());
        String icon = errors.text("icon", blank(req.icon()), false, 64);
        if (perm != null && !PERM.matcher(perm).matches()) {
            errors.add("permCode", ErrorCodes.PATTERN_MISMATCH, "权限点编码格式不正确");
        }
        errors.text("path", path, false, 128);
        errors.text("component", component, false, 128);
        if (type != null) {
            switch (type) {
                case "DIR" -> {
                    if (perm != null) {
                        errors.add("permCode", ErrorCodes.INVALID_VALUE, "目录不能填写权限点");
                    }
                    if (path != null || component != null) {
                        errors.add("path", ErrorCodes.INVALID_VALUE, "目录不能填写路由");
                    }
                }
                case "MENU" -> {
                    if (path == null) {
                        errors.add("path", ErrorCodes.REQUIRED, "菜单必须填写 path");
                    }
                    if (component == null) {
                        errors.add("component", ErrorCodes.REQUIRED, "菜单必须填写 component");
                    }
                }
                case "BUTTON" -> {
                    if (perm == null) {
                        errors.add("permCode", ErrorCodes.REQUIRED, "按钮必须填写权限点");
                    }
                    if (path != null || component != null) {
                        errors.add("path", ErrorCodes.INVALID_VALUE, "按钮不能填写路由");
                    }
                }
                default -> {
                    // already reported
                }
            }
        }
        errors.throwIfAny();
        int sort = req.sortNo() != null ? req.sortNo() : (existing == null ? 0 : existing.getSortNo());
        boolean visible = req.visible() != null ? req.visible()
                : (existing == null || Boolean.TRUE.equals(existing.getVisible()));
        boolean enabled = req.enabled() != null ? req.enabled()
                : (existing == null || Boolean.TRUE.equals(existing.getEnabled()));
        if (!create && existing == null) {
            throw ApiException.notFound("菜单");
        }
        return new Draft(type, req.parentId(), name, perm, path, component, icon, sort, visible, enabled);
    }

    private static void apply(SysMenu row, Draft draft) {
        row.setParentId(draft.parentId());
        row.setType(draft.type());
        row.setName(draft.name());
        row.setPermCode(draft.permCode());
        row.setPath(draft.path());
        row.setComponent(draft.component());
        row.setIcon(draft.icon());
        row.setSortNo(draft.sortNo());
        row.setVisible(draft.visible());
        row.setEnabled(draft.enabled());
    }

    private SysMenu require(UUID id) {
        SysMenu row = id == null ? null : menus.selectById(id);
        if (row == null) {
            throw ApiException.notFound("菜单");
        }
        return row;
    }

    private static List<MenuNode> build(List<SysMenu> rows) {
        Map<UUID, List<SysMenu>> children = new HashMap<>();
        List<SysMenu> roots = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (SysMenu row : rows) {
            ids.add(row.getId());
        }
        for (SysMenu row : rows) {
            if (row.getParentId() == null || !ids.contains(row.getParentId())) {
                roots.add(row);
            } else {
                children.computeIfAbsent(row.getParentId(), ignored -> new ArrayList<>()).add(row);
            }
        }
        Comparator<SysMenu> order = Comparator
                .comparing((SysMenu row) -> row.getSortNo() == null ? 0 : row.getSortNo())
                .thenComparing(SysMenu::getName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> row.getId().toString());
        roots.sort(order);
        return roots.stream().map(row -> node(row, children, order)).toList();
    }

    private static MenuNode node(SysMenu row, Map<UUID, List<SysMenu>> children, Comparator<SysMenu> order) {
        List<SysMenu> kids = new ArrayList<>(children.getOrDefault(row.getId(), List.of()));
        kids.sort(order);
        return MenuNode.of(row, kids.stream().map(kid -> node(kid, children, order)).toList());
    }

    private static ApiException duplicate(DuplicateKeyException ex, Draft draft) {
        String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("uk_sys_menu_perm_code")) {
            return ApiException.duplicatePermCode(draft.permCode());
        }
        if (message != null && message.contains("uk_sys_menu_sibling_path")) {
            return new ApiException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_CODE, "同级路由已存在：" + draft.path(),
                    List.of(new com.xihua.mes.common.error.FieldError(
                            "path", ErrorCodes.DUPLICATE_CODE, "同级路由已存在：" + draft.path())));
        }
        throw ex;
    }

    private static String parentMessage(String type) {
        return switch (type) {
            case "DIR" -> "目录的父节点只能是根或其他目录";
            case "MENU" -> "菜单的父节点只能是根或目录";
            case "BUTTON" -> "按钮的父节点必须是菜单";
            default -> "父节点不合法";
        };
    }

    private static void requireBody(Object body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "请求体不是合法 JSON 或字段类型不符",
                    List.of());
        }
    }

    private static String blank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Draft(
            String type,
            UUID parentId,
            String name,
            String permCode,
            String path,
            String component,
            String icon,
            int sortNo,
            boolean visible,
            boolean enabled) {
    }
}
