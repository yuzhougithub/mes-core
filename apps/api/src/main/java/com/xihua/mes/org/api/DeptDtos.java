package com.xihua.mes.org.api;

import java.util.List;
import java.util.UUID;

import com.xihua.mes.org.domain.Dept;

public final class DeptDtos {

    private DeptDtos() {
    }

    /**
     * {@code parentIdSpecified} is true only when the JSON contains {@code parentId}.
     * Omitting it on update leaves the parent unchanged; an explicit null moves the node to the root.
     */
    public static final class DeptWrite {
        private String code;
        private String name;
        private UUID parentId;
        private boolean parentIdSpecified;
        private UUID leaderPersonId;
        private Integer sortNo;
        private Boolean enabled;
        private Boolean shared;
        private Integer lockVersion;

        public String code() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public UUID parentId() {
            return parentId;
        }

        public void setParentId(UUID parentId) {
            this.parentId = parentId;
            this.parentIdSpecified = true;
        }

        public boolean parentIdSpecified() {
            return parentIdSpecified;
        }

        public UUID leaderPersonId() {
            return leaderPersonId;
        }

        public void setLeaderPersonId(UUID leaderPersonId) {
            this.leaderPersonId = leaderPersonId;
        }

        public Integer sortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }

        public Boolean enabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Boolean shared() {
            return shared;
        }

        public void setShared(Boolean shared) {
            this.shared = shared;
        }

        public Integer lockVersion() {
            return lockVersion;
        }

        public void setLockVersion(Integer lockVersion) {
            this.lockVersion = lockVersion;
        }
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
