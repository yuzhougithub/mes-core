package com.xihua.mes.org.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xihua.mes.auth.CurrentContext;
import com.xihua.mes.auth.PlantDirectory;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.Errors;
import com.xihua.mes.common.error.FieldError;
import com.xihua.mes.org.domain.Dept;
import com.xihua.mes.org.domain.Person;
import com.xihua.mes.org.infra.AssignmentRow;
import com.xihua.mes.org.infra.DeptMapper;
import com.xihua.mes.org.infra.PersonMapper;
import com.xihua.mes.org.infra.PersonPlantMapper;
import com.xihua.mes.org.infra.PersonPostMapper;
import com.xihua.mes.org.infra.PostMapper;

/**
 * Person rows owned by the account API. Creating or deleting a person without its account is
 * rejected by the database (exactly one non-deleted account per non-deleted person).
 */
@Service
public class PersonAccountService {

    private final PersonMapper persons;
    private final PersonPlantMapper assignments;
    private final PlantDirectory plants;
    private final DeptMapper depts;
    private final PostMapper posts;
    private final PersonPostMapper personPosts;

    public PersonAccountService(
            PersonMapper persons,
            PersonPlantMapper assignments,
            PlantDirectory plants,
            DeptMapper depts,
            PostMapper posts,
            PersonPostMapper personPosts) {
        this.persons = persons;
        this.assignments = assignments;
        this.plants = plants;
        this.depts = depts;
        this.posts = posts;
        this.personPosts = personPosts;
    }

    public Person require(UUID id) {
        Person person = persons.selectById(id);
        if (person == null) {
            throw ApiException.notFound("人员");
        }
        return person;
    }

    public Person findByCode(String code) {
        return persons.findByCode(code);
    }

    public boolean enabled(UUID personId) {
        Boolean enabled = persons.enabledFlag(personId);
        return Boolean.TRUE.equals(enabled);
    }

    public List<UUID> postIds(UUID personId) {
        return personPosts.postIds(personId);
    }

    @Transactional
    public Person insert(String code, String name, String mobile, String email, String remark, UUID deptId) {
        assertDept(deptId);
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setCode(code);
        person.setName(name);
        person.setMobile(mobile);
        person.setEmail(email);
        person.setEnabled(true);
        person.setRemark(remark);
        person.setDeptId(deptId);
        person.setLockVersion(0);
        person.setDeleted(false);
        persons.insert(person);
        return person;
    }

    @Transactional
    public void updateProfile(
            Person person, String name, String mobile, String email, String remark, boolean changeDept, UUID deptId) {
        person.setName(name);
        person.setMobile(mobile);
        person.setEmail(email);
        person.setRemark(remark);
        if (changeDept) {
            assertDept(deptId);
            person.setDeptId(deptId);
        }
        if (persons.updateById(person) != 1) {
            throw ApiException.staleVersion();
        }
    }

    @Transactional
    public void replacePosts(UUID personId, List<UUID> postIds) {
        List<UUID> ids = distinctRoles(postIds);
        if (!ids.isEmpty()) {
            List<UUID> found = posts.existingIds(ids);
            if (found.size() != ids.size()) {
                throw ApiException.validation(List.of(
                        new FieldError("postIds", ErrorCodes.INVALID_VALUE, "岗位不存在")));
            }
        }
        personPosts.deleteByPerson(personId);
        UUID actor = CurrentContext.userUuid();
        for (UUID postId : ids) {
            personPosts.insert(personId, postId, actor);
        }
    }

    private void assertDept(UUID deptId) {
        if (deptId == null) {
            return;
        }
        Dept dept = depts.findAny(deptId);
        if (dept == null) {
            throw ApiException.validation(List.of(
                    new FieldError("deptId", ErrorCodes.INVALID_VALUE, "部门不存在")));
        }
        UUID plantId = CurrentContext.requirePlantUuid();
        if (dept.getPlantId() != null && !plantId.equals(dept.getPlantId())) {
            throw ApiException.unprocessable(ErrorCodes.CROSS_PLANT_REFERENCE, "部门不属于当前工厂",
                    List.of(new FieldError("deptId", ErrorCodes.CROSS_PLANT_REFERENCE, "部门不属于当前工厂")));
        }
    }

    @Transactional
    public void logicalDelete(UUID id) {
        int rows = persons.update(null, new UpdateWrapper<Person>().set("deleted", true).eq("id", id));
        if (rows != 1) {
            throw ApiException.notFound("人员");
        }
    }

    public List<ActivePlant> activePlants(UUID personId) {
        return assignments.activePlants(personId).stream()
                .map(row -> new ActivePlant(row.getPlantId(), row.getPlantCode(), row.getPlantName(),
                        Boolean.TRUE.equals(row.getDefaultPlant())))
                .toList();
    }

    public boolean hasActive(UUID personId, UUID plantId) {
        return activePlants(personId).stream().anyMatch(plant -> plant.plantId().equals(plantId));
    }

    public List<String> roleCodes(UUID personId, UUID plantId) {
        return assignments.roleCodes(personId, plantId);
    }

    public List<AssignmentView> listAssignments(UUID personId) {
        List<AssignmentView> views = new ArrayList<>();
        for (AssignmentRow row : assignments.listByPerson(personId)) {
            views.add(new AssignmentView(
                    row.getId(),
                    row.getPlantId(),
                    row.getPlantCode(),
                    row.getPlantName(),
                    Boolean.TRUE.equals(row.getDefaultPlant()),
                    Boolean.TRUE.equals(row.getActive()),
                    row.getWorkshopId(),
                    row.getJobTitle(),
                    row.getLockVersion() == null ? 0 : row.getLockVersion(),
                    assignments.roleIds(row.getId())));
        }
        return views;
    }

    /**
     * Replace every assignment of the person. Exactly one row is the default, and that row is active.
     */
    @Transactional
    public UUID replaceAssignments(UUID personId, List<AssignmentDraft> drafts) {
        return replaceAssignments(personId, drafts, false);
    }

    /**
     * @param allowEmpty plant-scoped editors may remove the user from the current plant and leave
     *        no assignments; a full replace still requires exactly one default
     */
    public UUID replaceAssignments(UUID personId, List<AssignmentDraft> drafts, boolean allowEmpty) {
        List<AssignmentDraft> rows = drafts == null ? List.of() : drafts;
        if (rows.isEmpty() && allowEmpty) {
            assignments.deleteByPerson(personId);
            return null;
        }
        Errors errors = new Errors();
        Set<UUID> seen = new HashSet<>();
        int defaults = 0;
        for (int i = 0; i < rows.size(); i++) {
            AssignmentDraft draft = rows.get(i);
            String field = "plants[" + i + "]";
            if (draft.plantId() == null) {
                errors.add(field + ".plantId", ErrorCodes.REQUIRED, "plantId 必填");
                continue;
            }
            if (!seen.add(draft.plantId())) {
                errors.add(field + ".plantId", ErrorCodes.DUPLICATE, "工厂重复");
            }
            if (draft.jobTitle() != null && draft.jobTitle().codePointCount(0, draft.jobTitle().length()) > 64) {
                errors.add(field + ".jobTitle", ErrorCodes.TOO_LONG, "jobTitle 长度不能超过 64");
            }
            if (draft.defaultPlant()) {
                defaults++;
                if (!draft.active()) {
                    errors.add(field + ".isDefault", ErrorCodes.INVALID_VALUE, "默认工厂必须是有效任职");
                }
            }
        }
        if (defaults != 1) {
            errors.add("plants", ErrorCodes.INVALID_VALUE, "须且仅能指定一个默认工厂");
        }
        errors.throwIfAny();

        UUID defaultPlantId = null;
        for (AssignmentDraft draft : rows) {
            if (!plants.isActive(draft.plantId())) {
                throw ApiException.plantForbidden("工厂不存在或已停用：" + draft.plantId());
            }
            if (draft.workshopId() != null && !assignments.workshopEnabledInPlant(draft.workshopId(), draft.plantId())) {
                throw ApiException.unprocessable(ErrorCodes.CROSS_PLANT_REFERENCE, "车间不属于该工厂",
                        List.of(new FieldError("workshopId", ErrorCodes.CROSS_PLANT_REFERENCE, "车间不属于该工厂")));
            }
            List<UUID> roleIds = distinctRoles(draft.roleIds());
            if (!roleIds.isEmpty()) {
                List<UUID> found = assignments.existingRoleIds(roleIds);
                if (found.size() != roleIds.size()) {
                    throw ApiException.validation(List.of(
                            new FieldError("roleIds", ErrorCodes.INVALID_VALUE, "角色不存在")));
                }
            }
            if (draft.defaultPlant()) {
                defaultPlantId = draft.plantId();
            }
        }

        assignments.deleteByPerson(personId);
        UUID actor = CurrentContext.userUuid();
        for (AssignmentDraft draft : rows) {
            UUID id = UUID.randomUUID();
            assignments.insertAssignment(id, personId, draft.plantId(), draft.workshopId(), blankToNull(draft.jobTitle()),
                    draft.active(), draft.defaultPlant(), actor);
            for (UUID roleId : distinctRoles(draft.roleIds())) {
                assignments.insertRole(id, roleId, actor);
            }
        }
        return defaultPlantId;
    }

    private static List<UUID> distinctRoles(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AssignmentDraft(
            UUID plantId,
            boolean defaultPlant,
            boolean active,
            UUID workshopId,
            String jobTitle,
            List<UUID> roleIds) {
    }

    public record AssignmentView(
            UUID id,
            UUID plantId,
            String plantCode,
            String plantName,
            boolean defaultPlant,
            boolean active,
            UUID workshopId,
            String jobTitle,
            int lockVersion,
            List<UUID> roleIds) {
    }

    public record ActivePlant(UUID plantId, String code, String name, boolean defaultPlant) {
    }
}
