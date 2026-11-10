package com.xihua.mes.org.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xihua.mes.auth.CurrentContext;
import com.xihua.mes.auth.UserAdminGuard;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.Errors;
import com.xihua.mes.common.error.FieldError;
import com.xihua.mes.common.web.Locks;
import com.xihua.mes.org.api.DeptDtos.DeptMove;
import com.xihua.mes.org.api.DeptDtos.DeptNode;
import com.xihua.mes.org.api.DeptDtos.DeptWrite;
import com.xihua.mes.org.domain.Dept;
import com.xihua.mes.org.infra.DeptMapper;
import com.xihua.mes.org.infra.PersonMapper;

@Service
public class DeptService {

    private static final String TREE_LOCK =
            "SELECT pg_advisory_xact_lock(hashtext('org_dept_tree')::bigint)";

    private final DeptMapper depts;
    private final PersonMapper persons;
    private final JdbcTemplate jdbc;
    private final UserAdminGuard guard;

    public DeptService(DeptMapper depts, PersonMapper persons, JdbcTemplate jdbc, UserAdminGuard guard) {
        this.depts = depts;
        this.persons = persons;
        this.jdbc = jdbc;
        this.guard = guard;
    }

    public List<DeptNode> tree() {
        List<Dept> rows = depts.selectList(new QueryWrapper<Dept>().orderByAsc("sort_no", "code", "id"));
        Map<UUID, List<Dept>> children = new HashMap<>();
        List<Dept> roots = new ArrayList<>();
        for (Dept row : rows) {
            if (row.getParentId() == null) {
                roots.add(row);
            } else {
                children.computeIfAbsent(row.getParentId(), ignored -> new ArrayList<>()).add(row);
            }
        }
        Comparator<Dept> order = Comparator
                .comparing((Dept row) -> row.getSortNo() == null ? 0 : row.getSortNo())
                .thenComparing(Dept::getCode, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> row.getId().toString());
        roots.sort(order);
        return roots.stream().map(row -> toNode(row, children, order)).toList();
    }

    @Transactional
    public DeptNode create(DeptWrite req) {
        lockTree();
        requireBody(req);
        Draft draft = draft(req, true);
        if (draft.plantId() == null) {
            requireGroupWriter("org:dept:create");
        }
        Dept parent = resolveParent(null, draft.parentId(), draft.plantId());
        Dept row = new Dept();
        row.setId(UUID.randomUUID());
        row.setPlantId(draft.plantId());
        row.setParentId(draft.parentId());
        row.setCode(draft.code());
        row.setName(draft.name());
        row.setAncestors(ancestors(parent, row.getId()));
        row.setLeaderPersonId(draft.leaderPersonId());
        row.setSortNo(draft.sortNo());
        row.setEnabled(draft.enabled());
        row.setLockVersion(0);
        row.setDeleted(false);
        try {
            depts.insert(row);
        } catch (DuplicateKeyException ex) {
            throw ApiException.duplicateCode(draft.code());
        }
        return DeptNode.of(require(row.getId()), List.of());
    }

    @Transactional
    public DeptNode update(UUID id, DeptWrite req) {
        lockTree();
        requireBody(req);
        Dept row = require(id);
        if (row.getPlantId() == null) {
            requireGroupWriter("org:dept:update");
        }
        Locks.check(req.lockVersion(), row.getLockVersion());
        Draft draft = draft(req, false);
        if (req.parentIdSpecified()) {
            applyParent(row, req.parentId());
        }
        row.setName(draft.name());
        row.setLeaderPersonId(draft.leaderPersonId());
        row.setSortNo(draft.sortNo());
        row.setEnabled(draft.enabled());
        Locks.requireUpdated(depts.updateById(row));
        return DeptNode.of(require(id), List.of());
    }

    @Transactional
    public DeptNode move(UUID id, DeptMove req) {
        lockTree();
        requireBody(req);
        Dept row = require(id);
        if (row.getPlantId() == null) {
            requireGroupWriter("org:dept:update");
        }
        Locks.check(req.lockVersion(), row.getLockVersion());
        applyParent(row, req.parentId());
        Locks.requireUpdated(depts.updateById(row));
        return DeptNode.of(require(id), List.of());
    }

    @Transactional
    public void delete(UUID id, Integer lockVersion) {
        lockTree();
        Dept row = require(id);
        if (row.getPlantId() == null) {
            requireGroupWriter("org:dept:delete");
        }
        Locks.check(lockVersion, row.getLockVersion());
        if (depts.childCount(id) > 0 || depts.personCount(id) > 0) {
            throw ApiException.deptNotEmpty();
        }
        Locks.requireUpdated(depts.update(null, new UpdateWrapper<Dept>()
                .set("deleted", true).eq("id", id).eq("lock_version", lockVersion)));
    }

    private void applyParent(Dept row, UUID parentId) {
        if (Objects.equals(row.getParentId(), parentId)) {
            return;
        }
        Dept parent = resolveParent(row.getId(), parentId, row.getPlantId());
        String oldPrefix = row.getAncestors();
        String newPrefix = ancestors(parent, row.getId());
        depts.rewriteDescendants(row.getId(), oldPrefix, newPrefix, CurrentContext.userUuid());
        row.setParentId(parentId);
        row.setAncestors(newPrefix);
    }

    private Dept resolveParent(UUID selfId, UUID parentId, UUID plantId) {
        if (parentId == null) {
            return null;
        }
        if (selfId != null && selfId.equals(parentId)) {
            throw parentInvalid("不能把部门移到自身下面");
        }
        Dept parent = depts.findAny(parentId);
        if (parent == null) {
            throw parentInvalid("父部门不存在");
        }
        if (!samePlant(parent.getPlantId(), plantId)) {
            throw ApiException.unprocessable(ErrorCodes.CROSS_PLANT_REFERENCE, "父部门必须属于同一工厂",
                    List.of(new FieldError("parentId", ErrorCodes.CROSS_PLANT_REFERENCE, "父部门必须属于同一工厂")));
        }
        if (selfId != null && parent.getAncestors() != null
                && parent.getAncestors().contains("/" + selfId + "/")) {
            throw parentInvalid("不能把部门移到自己的子孙下面");
        }
        return parent;
    }

    private Draft draft(DeptWrite req, boolean create) {
        Errors errors = new Errors();
        String code = null;
        if (create) {
            code = errors.text("code", req.code(), true, 32);
            if (code != null) {
                code = code.trim();
            }
        }
        String name = errors.text("name", req.name(), true, 64);
        if (name != null) {
            name = name.trim();
        }
        errors.throwIfAny();
        assertLeader(req.leaderPersonId());
        UUID plantId = create
                ? (Boolean.TRUE.equals(req.shared()) ? null : CurrentContext.requirePlantUuid())
                : null;
        return new Draft(code, name, req.parentId(), req.leaderPersonId(),
                req.sortNo() == null ? 0 : req.sortNo(), req.enabled() == null || req.enabled(), plantId);
    }

    private void assertLeader(UUID leaderPersonId) {
        if (leaderPersonId == null) {
            return;
        }
        if (persons.selectById(leaderPersonId) == null) {
            throw ApiException.validation(List.of(
                    new FieldError("leaderPersonId", ErrorCodes.INVALID_VALUE, "负责人不存在")));
        }
    }

    private Dept require(UUID id) {
        Dept row = id == null ? null : depts.selectById(id);
        if (row == null) {
            throw ApiException.notFound("部门");
        }
        return row;
    }

    /** Group-level departments ({@code plant_id} null) are superadmin-only. Plant departments are not. */
    private void requireGroupWriter(String perm) {
        if (!guard.superAdmin()) {
            throw ApiException.permissionDenied(perm);
        }
    }

    private void lockTree() {
        jdbc.execute(TREE_LOCK);
    }

    private static String ancestors(Dept parent, UUID id) {
        String self = "/" + id + "/";
        if (parent == null) {
            return self;
        }
        return parent.getAncestors() + id + "/";
    }

    private static boolean samePlant(UUID left, UUID right) {
        return left == null ? right == null : left.equals(right);
    }

    private static ApiException parentInvalid(String detail) {
        return ApiException.validation(List.of(new FieldError("parentId", ErrorCodes.INVALID_VALUE, detail)));
    }

    private static DeptNode toNode(Dept row, Map<UUID, List<Dept>> children, Comparator<Dept> order) {
        List<Dept> kids = new ArrayList<>(children.getOrDefault(row.getId(), List.of()));
        kids.sort(order);
        return DeptNode.of(row, kids.stream().map(kid -> toNode(kid, children, order)).toList());
    }

    private static void requireBody(Object body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "请求体不是合法 JSON 或字段类型不符",
                    List.of());
        }
    }

    private record Draft(
            String code,
            String name,
            UUID parentId,
            UUID leaderPersonId,
            int sortNo,
            boolean enabled,
            UUID plantId) {
    }
}
