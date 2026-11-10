package com.xihua.mes.org.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xihua.mes.auth.CurrentContext;
import com.xihua.mes.auth.infra.SysConfigMapper;
import com.xihua.mes.common.persistence.CrossPlant;
import com.xihua.mes.common.persistence.DataScopeBridge;
import com.xihua.mes.common.persistence.DataScopeDecision;
import com.xihua.mes.org.api.OrgTreeDtos.OrgNode;
import com.xihua.mes.org.infra.OrgTreeMapper;
import com.xihua.mes.org.infra.OrgTreeMapper.LineRow;
import com.xihua.mes.org.infra.OrgTreeMapper.PlantRow;
import com.xihua.mes.org.infra.OrgTreeMapper.WorkshopRow;

/** Group → plant → workshop → line (work centers whose type is {@code LINE}). */
@Service
public class OrgTreeService {

    private final OrgTreeMapper trees;
    private final SysConfigMapper configs;

    public OrgTreeService(OrgTreeMapper trees, SysConfigMapper configs) {
        this.trees = trees;
        this.configs = configs;
    }

    @CrossPlant
    public OrgNode tree() {
        DataScopeDecision decision = DataScopeBridge.current();
        Set<UUID> plants = visiblePlants(decision);
        Map<UUID, List<LineRow>> linesByWorkshop = new HashMap<>();
        for (LineRow line : trees.lines()) {
            if (plants != null && !plants.contains(line.getPlantId())) {
                continue;
            }
            linesByWorkshop.computeIfAbsent(line.getWorkshopId(), ignored -> new ArrayList<>()).add(line);
        }
        Map<UUID, List<OrgNode>> workshopsByPlant = new HashMap<>();
        for (WorkshopRow workshop : trees.workshops()) {
            if (plants != null && !plants.contains(workshop.getPlantId())) {
                continue;
            }
            List<OrgNode> lines = linesByWorkshop.getOrDefault(workshop.getId(), List.of()).stream()
                    .map(line -> new OrgNode("LINE", line.getId(), line.getCode(), line.getName(), List.of()))
                    .toList();
            workshopsByPlant.computeIfAbsent(workshop.getPlantId(), ignored -> new ArrayList<>())
                    .add(new OrgNode("WORKSHOP", workshop.getId(), workshop.getCode(), workshop.getName(), lines));
        }
        List<OrgNode> plantNodes = new ArrayList<>();
        for (PlantRow plant : trees.plants()) {
            if (plants != null && !plants.contains(plant.getId())) {
                continue;
            }
            plantNodes.add(new OrgNode("PLANT", plant.getId(), plant.getCode(), plant.getName(),
                    workshopsByPlant.getOrDefault(plant.getId(), List.of())));
        }
        String group = configs.findValue("org.group.name");
        if (group == null || group.isBlank()) {
            group = "集团";
        }
        return new OrgNode("GROUP", null, null, group, plantNodes);
    }

    /** Null means every non-deleted plant (super admin / header mode on this cross-plant read). */
    private static Set<UUID> visiblePlants(DataScopeDecision decision) {
        if (decision.unrestricted()) {
            return null;
        }
        if (decision.all()) {
            return new HashSet<>(decision.assignedPlantIds());
        }
        return Set.of(CurrentContext.requirePlantUuid());
    }
}
