package com.xihua.mes.org.api;

import java.util.List;
import java.util.UUID;

import com.xihua.mes.org.domain.Dept;

public final class DeptDtos {

    private DeptDtos() {
    }

    public record DeptWrite(
            String code,
            String name,
            UUID parentId,
            UUID leaderPersonId,
            Integer sortNo,
            Boolean enabled,
            Boolean shared,
            Integer lockVersion) {
    }

    public record DeptMove(UUID parentId, Integer lockVersion) {
    }

    public record DeptNode(
            UUID id,
            UUID plantId,
            UUID parentId,
            String code,
            String name,
            String ancestors,
            UUID leaderPersonId,
            int sortNo,
            boolean enabled,
            int lockVersion,
            List<DeptNode> children) {

        public static DeptNode of(Dept row, List<DeptNode> children) {
            return new DeptNode(
                    row.getId(),
                    row.getPlantId(),
                    row.getParentId(),
                    row.getCode(),
                    row.getName(),
                    row.getAncestors(),
                    row.getLeaderPersonId(),
                    row.getSortNo() == null ? 0 : row.getSortNo(),
                    Boolean.TRUE.equals(row.getEnabled()),
                    row.getLockVersion() == null ? 0 : row.getLockVersion(),
                    children);
        }
    }
}
