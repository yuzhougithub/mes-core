package com.xihua.mes.common.persistence;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xihua.mes.auth.CurrentContext;
import com.xihua.mes.auth.NoPlant;
import com.xihua.mes.auth.PlantContextFilter;
import com.xihua.mes.auth.infra.DataScopeMapper;

/** Loads {@link DataScopeDecision} once per request. Nested SQL while loading is not scoped. */
@Component
public class DataScopeResolver {

    private static final ThreadLocal<DataScopeDecision> CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> LOADING = new ThreadLocal<>();

    private final DataScopeMapper mapper;
    private final boolean headerMode;

    public DataScopeResolver(
            DataScopeMapper mapper,
            @Value("${mes.auth.mode:jwt}") String mode) {
        this.mapper = mapper;
        this.headerMode = PlantContextFilter.MODE_HEADER.equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    @PostConstruct
    void bind() {
        DataScopeBridge.bind(this);
    }

    public DataScopeDecision current() {
        DataScopeDecision cached = CACHE.get();
        if (cached != null) {
            return cached;
        }
        if (Boolean.TRUE.equals(LOADING.get())) {
            return DataScopeDecision.loading();
        }
        LOADING.set(true);
        try {
            DataScopeDecision loaded = load();
            CACHE.set(loaded);
            return loaded;
        } finally {
            LOADING.remove();
        }
    }

    public void clear() {
        CACHE.remove();
        LOADING.remove();
    }

    private DataScopeDecision load() {
        if (headerMode) {
            return new DataScopeDecision(true, false, Set.of(), null, null, CurrentContext.userUuid(),
                    List.of(), List.of());
        }
        UUID userId = CurrentContext.userUuid();
        UUID personId = CurrentContext.personUuid();
        if (userId == null) {
            return new DataScopeDecision(false, false, Set.of(), null, null, null, List.of(), List.of());
        }
        if (Boolean.TRUE.equals(mapper.superAdmin(userId))) {
            List<UUID> plants = personId == null ? List.of() : mapper.assignedPlantIds(personId);
            return new DataScopeDecision(true, false, Set.of(), null, null, userId, List.of(), plants);
        }
        UUID plantId = currentPlant();
        List<String> rawScopes = personId == null || plantId == null
                ? List.of()
                : mapper.scopes(personId, plantId);
        Set<String> scopes = new HashSet<>();
        if (rawScopes != null) {
            for (String scope : rawScopes) {
                if (scope != null) {
                    scopes.add(scope);
                }
            }
        }
        boolean all = scopes.contains(DataScopeDecision.ALL);
        UUID deptId = personId == null ? null : mapper.deptId(personId);
        String ancestors = deptId == null ? null : mapper.ancestors(deptId);
        List<UUID> custom = !scopes.contains(DataScopeDecision.CUSTOM) || personId == null || plantId == null
                ? List.of()
                : List.copyOf(mapper.customDeptIds(personId, plantId));
        List<UUID> plants = personId == null ? List.of() : List.copyOf(mapper.assignedPlantIds(personId));
        return new DataScopeDecision(false, all, Set.copyOf(scopes), deptId, ancestors, userId, custom, plants);
    }

    private static UUID currentPlant() {
        String raw = CurrentContext.plantId();
        if (raw == null) {
            return null;
        }
        try {
            UUID plantId = UUID.fromString(raw);
            return NoPlant.ID.equals(plantId) ? null : plantId;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
