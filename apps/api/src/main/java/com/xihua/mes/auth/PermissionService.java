package com.xihua.mes.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.method.HandlerMethod;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xihua.mes.auth.api.AuthDtos.MeMenu;
import com.xihua.mes.auth.infra.MenuRow;
import com.xihua.mes.auth.infra.PermissionMapper;
import com.xihua.mes.auth.infra.UserPermState;
import com.xihua.mes.common.error.ApiException;

/**
 * Permission snapshot cached by {@code (userId, plantId, ver)}. {@code ver} is the current
 * {@code sys_user.perm_version}, not the access-token claim, so a grant change applies on the
 * next request of an already issued token.
 */
@Service
public class PermissionService implements PermVersions {

    private final PermissionMapper mapper;
    private final Clock clock;
    private final boolean headerMode;
    private final Cache<CacheKey, Snapshot> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public PermissionService(
            PermissionMapper mapper,
            Clock clock,
            @Value("${mes.auth.mode:jwt}") String mode) {
        this.mapper = mapper;
        this.clock = clock;
        this.headerMode = PlantContextFilter.MODE_HEADER.equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    public void check(HandlerMethod method) {
        RequiresPerm perm = method.getMethodAnnotation(RequiresPerm.class);
        if (perm == null) {
            perm = method.getBeanType().getAnnotation(RequiresPerm.class);
        }
        if (perm == null || perm.value().length == 0) {
            return;
        }
        if (perm.global()) {
            requireGlobal(perm.value()[0]);
            return;
        }
        require(perm.anyOf(), perm.value());
    }

    /**
     * Non-superadmin menu grant: every selected node's {@code perm_code} must already be held
     * in the current plant. {@code DIR} nodes carry no permission and are exempt.
     */
    public void requireMenusWithinCaller(Collection<MenuGrant> selected) {
        if (selected == null || selected.isEmpty() || callerUnrestricted()) {
            return;
        }
        Set<String> mine = callerPerms();
        for (MenuGrant menu : selected) {
            if (menu == null || "DIR".equals(menu.type()) || menu.permCode() == null) {
                continue;
            }
            if (!mine.contains(menu.permCode())) {
                throw ApiException.permissionDenied(menu.permCode());
            }
        }
    }

    /**
     * Non-superadmin role assignment: each role's permission points must be a subset of the
     * caller's permissions in the current plant.
     */
    public void requireRolesWithinCaller(Collection<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty() || callerUnrestricted()) {
            return;
        }
        Set<String> mine = callerPerms();
        for (UUID roleId : roleIds) {
            if (roleId == null) {
                continue;
            }
            for (String code : mapper.rolePermCodes(roleId)) {
                if (!mine.contains(code)) {
                    throw ApiException.permissionDenied(code);
                }
            }
        }
    }

    private void requireGlobal(String code) {
        if (headerMode) {
            return;
        }
        UUID userId = CurrentContext.userUuid();
        if (userId == null) {
            throw ApiException.permissionDenied(code);
        }
        UserPermState state = mapper.userState(userId);
        if (state == null || !Boolean.TRUE.equals(state.getSuperAdmin())) {
            throw ApiException.permissionDenied(code);
        }
    }

    private boolean callerUnrestricted() {
        if (headerMode) {
            return true;
        }
        UUID userId = CurrentContext.userUuid();
        if (userId == null) {
            return false;
        }
        UserPermState state = mapper.userState(userId);
        return state != null && Boolean.TRUE.equals(state.getSuperAdmin());
    }

    /** Codes linked to the caller's roles in the current plant, including ones a disabled parent turns off. */
    private Set<String> callerPerms() {
        UUID personId = CurrentContext.personUuid();
        UUID plantId = CurrentContext.requirePlantUuid();
        if (personId == null || NoPlant.ID.equals(plantId)) {
            return Set.of();
        }
        return Set.copyOf(mapper.heldPermCodes(personId, plantId));
    }

    public record MenuGrant(String type, String permCode) {
    }

    /** Shared master-data writes ({@code plant_id} null) need {@link PermCodes#SHARED_WRITE}. */
    public void requireSharedMaster(UUID recordPlantId) {
        if (recordPlantId == null) {
            require(false, PermCodes.SHARED_WRITE);
        }
    }

    public void require(boolean anyOf, String... codes) {
        if (headerMode || codes == null || codes.length == 0) {
            return;
        }
        UUID userId = CurrentContext.userUuid();
        if (userId == null) {
            throw ApiException.permissionDenied(codes[0]);
        }
        UserPermState state = mapper.userState(userId);
        if (state == null) {
            throw ApiException.permissionDenied(codes[0]);
        }
        if (Boolean.TRUE.equals(state.getSuperAdmin())) {
            return;
        }
        UUID plantId = CurrentContext.requirePlantUuid();
        if (NoPlant.ID.equals(plantId)) {
            throw ApiException.permissionDenied(codes[0]);
        }
        Snapshot snapshot = snapshot(userId, CurrentContext.personUuid(), plantId, version(state));
        if (anyOf) {
            for (String code : codes) {
                if (snapshot.perms().contains(code)) {
                    return;
                }
            }
            throw ApiException.permissionDenied(String.join(" | ", codes));
        }
        for (String code : codes) {
            if (!snapshot.perms().contains(code)) {
                throw ApiException.permissionDenied(code);
            }
        }
    }

    /** Menus and permission codes for {@code GET /auth/me}. */
    public MeAccess accessFor(boolean superAdmin, UUID userId, UUID personId, UUID plantId) {
        if (superAdmin) {
            return fullAccess();
        }
        if (userId == null || personId == null || plantId == null || NoPlant.ID.equals(plantId)) {
            return new MeAccess(List.of(), List.of());
        }
        UserPermState state = mapper.userState(userId);
        int version = state == null ? 0 : version(state);
        Snapshot snapshot = snapshot(userId, personId, plantId, version);
        return new MeAccess(sorted(snapshot.perms()), tree(mapper.menus(), snapshot.perms(), false));
    }

    /** Header mode is the dev stand-in for a caller who holds every permission point. */
    public MeAccess headerAccess() {
        return fullAccess();
    }

    @Override
    public void bumpRole(UUID roleId) {
        mapper.bumpRoleHolders(roleId, OffsetDateTime.now(clock));
        afterCommit(cache::invalidateAll);
    }

    @Override
    public void bumpMenu(UUID menuId) {
        mapper.bumpMenuHolders(menuId, OffsetDateTime.now(clock));
        afterCommit(cache::invalidateAll);
    }

    private MeAccess fullAccess() {
        List<MenuRow> rows = mapper.menus();
        Set<String> perms = new HashSet<>();
        for (MenuRow row : rows) {
            if (Boolean.TRUE.equals(row.getEnabled()) && row.getPermCode() != null) {
                perms.add(row.getPermCode());
            }
        }
        return new MeAccess(sorted(perms), tree(rows, perms, true));
    }

    private Snapshot snapshot(UUID userId, UUID personId, UUID plantId, int version) {
        return cache.get(new CacheKey(userId, plantId, version), key -> {
            if (personId == null) {
                return new Snapshot(Set.of());
            }
            return new Snapshot(effective(mapper.permCodes(personId, plantId), mapper.menus()));
        });
    }

    /**
     * A permission point is effective only while the menu and every ancestor are enabled.
     * A disabled parent therefore turns off its descendant buttons.
     */
    static Set<String> effective(Collection<String> granted, List<MenuRow> rows) {
        if (granted == null || granted.isEmpty()) {
            return Set.of();
        }
        Map<UUID, MenuRow> byId = new HashMap<>();
        Map<String, MenuRow> byCode = new HashMap<>();
        for (MenuRow row : rows) {
            byId.put(row.getId(), row);
            if (row.getPermCode() != null) {
                byCode.put(row.getPermCode(), row);
            }
        }
        Set<String> kept = new HashSet<>();
        for (String code : granted) {
            MenuRow row = byCode.get(code);
            if (row != null && ancestorsEnabled(row, byId)) {
                kept.add(code);
            }
        }
        return Set.copyOf(kept);
    }

    static boolean ancestorsEnabled(MenuRow row, Map<UUID, MenuRow> byId) {
        MenuRow current = row;
        Set<UUID> seen = new HashSet<>();
        while (current != null) {
            if (!Boolean.TRUE.equals(current.getEnabled())) {
                return false;
            }
            if (!seen.add(current.getId())) {
                return false;
            }
            if (current.getParentId() == null) {
                return true;
            }
            current = byId.get(current.getParentId());
            if (current == null) {
                return false;
            }
        }
        return false;
    }

    private static int version(UserPermState state) {
        return state.getPermVersion() == null ? 0 : state.getPermVersion();
    }

    private static List<String> sorted(Set<String> perms) {
        List<String> codes = new ArrayList<>(perms);
        codes.sort(Comparator.naturalOrder());
        return List.copyOf(codes);
    }

    static List<MeMenu> tree(List<MenuRow> rows, Set<String> perms, boolean allAccess) {
        Map<UUID, MenuRow> byId = new HashMap<>();
        for (MenuRow row : rows) {
            byId.put(row.getId(), row);
        }
        Set<UUID> included = new HashSet<>();
        for (MenuRow row : rows) {
            if (!chainShown(row, byId)) {
                continue;
            }
            if (allAccess || (row.getPermCode() != null && perms.contains(row.getPermCode()))) {
                included.add(row.getId());
            }
        }
        if (!allAccess) {
            List<UUID> seeds = List.copyOf(included);
            for (UUID id : seeds) {
                UUID parentId = byId.get(id).getParentId();
                while (parentId != null && included.add(parentId)) {
                    MenuRow parent = byId.get(parentId);
                    if (parent == null || !chainShown(parent, byId)) {
                        included.remove(parentId);
                        break;
                    }
                    parentId = parent.getParentId();
                }
            }
        }
        Map<UUID, List<MenuRow>> children = new HashMap<>();
        List<MenuRow> roots = new ArrayList<>();
        for (MenuRow row : rows) {
            if (!included.contains(row.getId())) {
                continue;
            }
            if (row.getParentId() == null || !included.contains(row.getParentId())) {
                roots.add(row);
            } else {
                children.computeIfAbsent(row.getParentId(), ignored -> new ArrayList<>()).add(row);
            }
        }
        Comparator<MenuRow> order = Comparator
                .comparing((MenuRow row) -> row.getSortNo() == null ? 0 : row.getSortNo())
                .thenComparing(MenuRow::getName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> row.getId().toString());
        roots.sort(order);
        return roots.stream().map(row -> toMenu(row, children, order)).toList();
    }

    private static boolean chainShown(MenuRow row, Map<UUID, MenuRow> byId) {
        MenuRow current = row;
        Set<UUID> seen = new HashSet<>();
        while (current != null) {
            if (!Boolean.TRUE.equals(current.getEnabled()) || !Boolean.TRUE.equals(current.getVisible())) {
                return false;
            }
            if (!seen.add(current.getId())) {
                return false;
            }
            UUID parentId = current.getParentId();
            current = parentId == null ? null : byId.get(parentId);
            if (parentId != null && current == null) {
                return false;
            }
        }
        return true;
    }

    private static MeMenu toMenu(MenuRow row, Map<UUID, List<MenuRow>> children, Comparator<MenuRow> order) {
        List<MenuRow> kids = new ArrayList<>(children.getOrDefault(row.getId(), List.of()));
        kids.sort(order);
        return new MeMenu(
                row.getId().toString(),
                row.getParentId() == null ? null : row.getParentId().toString(),
                row.getType(),
                row.getName(),
                row.getPermCode(),
                row.getPath(),
                row.getComponent(),
                row.getIcon(),
                row.getSortNo() == null ? 0 : row.getSortNo(),
                Boolean.TRUE.equals(row.getVisible()),
                kids.stream().map(kid -> toMenu(kid, children, order)).toList());
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    public record MeAccess(List<String> permissions, List<MeMenu> menus) {
    }

    private record Snapshot(Set<String> perms) {
    }

    private record CacheKey(UUID userId, UUID plantId, int version) {
    }
}
