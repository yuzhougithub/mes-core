package com.xihua.mes.auth;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xihua.mes.auth.api.UserDtos.PasswordReset;
import com.xihua.mes.auth.api.UserDtos.PersonView;
import com.xihua.mes.auth.api.UserDtos.PlantBindingInput;
import com.xihua.mes.auth.api.UserDtos.PlantBindingView;
import com.xihua.mes.auth.api.UserDtos.PlantReplace;
import com.xihua.mes.auth.api.UserDtos.ResetPassword;
import com.xihua.mes.auth.api.UserDtos.UserCreate;
import com.xihua.mes.auth.api.UserDtos.UserDetail;
import com.xihua.mes.auth.api.UserDtos.UserSummary;
import com.xihua.mes.auth.api.UserDtos.UserUpdate;
import com.xihua.mes.auth.domain.SysUser;
import com.xihua.mes.auth.infra.RefreshTokenMapper;
import com.xihua.mes.auth.infra.SysUserMapper;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.Errors;
import com.xihua.mes.common.error.FieldError;
import com.xihua.mes.common.web.Locks;
import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;
import com.xihua.mes.org.app.PersonAccountService;
import com.xihua.mes.org.app.PersonAccountService.AssignmentDraft;
import com.xihua.mes.org.app.PersonAccountService.AssignmentView;
import com.xihua.mes.org.domain.Person;
import com.xihua.mes.org.infra.PersonMapper;

/** User administration. Each user is created in the same transaction as its person. */
@Service
public class UserService {

    private static final Map<String, String> SORT = Map.of(
            "username", "username", "createdAt", "created_at", "updatedAt", "updated_at");

    private final SysUserMapper users;
    private final PersonMapper persons;
    private final PersonAccountService accounts;
    private final RefreshTokenMapper refreshTokens;
    private final UserAdminGuard guard;
    private final PasswordEncoder encoder;
    private final SecurityPolicyService policies;
    private final Clock clock;

    public UserService(
            SysUserMapper users,
            PersonMapper persons,
            PersonAccountService accounts,
            RefreshTokenMapper refreshTokens,
            UserAdminGuard guard,
            PasswordEncoder encoder,
            SecurityPolicyService policies,
            Clock clock) {
        this.users = users;
        this.persons = persons;
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.guard = guard;
        this.encoder = encoder;
        this.policies = policies;
        this.clock = clock;
    }

    public PageResult<UserSummary> list(PageQuery page, String q, String status) {
        guard.check("sys:user:list");
        QueryWrapper<SysUser> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
                throw ApiException.validation(List.of(
                        new FieldError("status", ErrorCodes.INVALID_VALUE, "status 只能是 ACTIVE 或 DISABLED")));
            }
            qw.eq("status", status);
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            qw.apply("""
                    (username ILIKE {0} OR person_id IN (
                        SELECT id FROM org_person WHERE NOT deleted AND (code ILIKE {0} OR name ILIKE {0})))
                    """, pattern);
        }
        if (!guard.superAdmin()) {
            qw.apply("""
                    person_id IN (
                        SELECT person_id FROM org_person_plant WHERE plant_id = {0} AND active)
                    """, CurrentContext.requirePlantUuid());
        }
        long total = users.selectCount(qw);
        page.applyOrderAndLimit(qw, SORT, "username");
        List<SysUser> rows = users.selectList(qw);
        Map<UUID, Person> people = loadPeople(rows);
        List<UserSummary> items = rows.stream().map(user -> {
            Person person = people.get(user.getPersonId());
            return new UserSummary(user.getId(), user.getUsername(), user.getStatus(),
                    Boolean.TRUE.equals(user.getSuperAdmin()), Boolean.TRUE.equals(user.getMustChangePwd()),
                    user.getPersonId(), person == null ? null : person.getCode(),
                    person == null ? null : person.getName(), user.getDefaultPlantId(),
                    user.getLockVersion() == null ? 0 : user.getLockVersion(),
                    user.getCreatedAt(), user.getUpdatedAt());
        }).toList();
        return new PageResult<>(items, page.page(), page.size(), total);
    }

    public UserDetail get(UUID id) {
        guard.check("sys:user:list");
        SysUser user = require(id);
        requireInCurrentPlant(user);
        return detail(user, null);
    }

    @Transactional
    public UserDetail create(UserCreate req) {
        guard.check("sys:user:create");
        requireBody(req);
        Errors errors = new Errors();
        String code = trim(errors.text("code", req.code(), true, 32));
        String name = errors.text("name", req.name(), true, 64);
        String mobile = errors.text("mobile", req.mobile(), false, 32);
        String email = errors.text("email", req.email(), false, 128);
        String remark = errors.text("remark", req.remark(), false, 500);
        checkEmail(errors, email);
        String username = normalizeUsername(req.username());
        if (username.isEmpty() && code != null) {
            username = code.toLowerCase(Locale.ROOT);
        }
        if (!validUsername(username)) {
            errors.add("username", ErrorCodes.PATTERN_MISMATCH,
                    "用户名须为 3–64 位小写字母、数字、.、_ 或 -，且以字母或数字开头");
        }
        errors.throwIfAny();
        if (Boolean.TRUE.equals(req.superAdmin()) && !guard.superAdmin()) {
            throw ApiException.permissionDenied("sys:user:grant");
        }
        List<AssignmentDraft> createPlants = drafts(req.plants());
        if (!guard.superAdmin()) {
            UUID current = CurrentContext.requirePlantUuid();
            if (createPlants.size() != 1 || createPlants.get(0).plantId() == null
                    || !current.equals(createPlants.get(0).plantId())) {
                throw ApiException.permissionDenied("sys:user:grant");
            }
        }
        if (users.findByUsername(username) != null) {
            throw duplicateUsername(username);
        }
        if (accounts.findByCode(code) != null) {
            throw ApiException.duplicateCode(code);
        }
        SecurityPolicy policy = policies.current();
        boolean generated = req.password() == null || req.password().isBlank();
        String password = generated ? PasswordPolicy.generate(username, policy) : req.password();
        PasswordPolicy.check(password, username, policy);
        Person person = accounts.insert(code, name, mobile, email, remark);
        UUID defaultPlant = accounts.replaceAssignments(person.getId(), createPlants);
        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPersonId(person.getId());
        user.setPasswordHash(encoder.encode(password));
        user.setStatus("ACTIVE");
        user.setSuperAdmin(Boolean.TRUE.equals(req.superAdmin()));
        user.setMustChangePwd(true);
        user.setFailCount(0);
        user.setPermVersion(0);
        user.setDefaultPlantId(defaultPlant);
        user.setLockVersion(0);
        user.setDeleted(false);
        users.insert(user);
        return detail(require(user.getId()), generated ? password : null);
    }

    @Transactional
    public UserDetail update(UUID id, UserUpdate req) {
        guard.check("sys:user:update");
        requireBody(req);
        SysUser user = require(id);
        requireInCurrentPlant(user);
        protectSuper(user);
        Locks.check(req.lockVersion(), user.getLockVersion());
        Errors errors = new Errors();
        String name = errors.text("name", req.name(), true, 64);
        String mobile = errors.text("mobile", req.mobile(), false, 32);
        String email = errors.text("email", req.email(), false, 128);
        String remark = errors.text("remark", req.remark(), false, 500);
        checkEmail(errors, email);
        String username = normalizeUsername(req.username());
        if (!validUsername(username)) {
            errors.add("username", ErrorCodes.PATTERN_MISMATCH,
                    "用户名须为 3–64 位小写字母、数字、.、_ 或 -，且以字母或数字开头");
        }
        errors.throwIfAny();
        if (!username.equals(user.getUsername()) && users.findByUsername(username) != null) {
            throw duplicateUsername(username);
        }
        if (users.updateUsername(id, username, user.getLockVersion(), now()) != 1) {
            throw ApiException.staleVersion();
        }
        accounts.updateProfile(accounts.require(user.getPersonId()), name, mobile, email, remark);
        return detail(require(id), null);
    }

    @Transactional
    public void delete(UUID id, Integer lockVersion) {
        guard.check("sys:user:delete");
        SysUser user = require(id);
        requireInCurrentPlant(user);
        protectSuper(user);
        Locks.check(lockVersion, user.getLockVersion());
        if (id.equals(CurrentContext.userUuid())) {
            throw ApiException.stateConflict("不能删除当前登录账号");
        }
        if (Boolean.TRUE.equals(user.getSuperAdmin()) && users.otherActiveSuperAdmins(id) == 0) {
            throw ApiException.stateConflict("不能删除最后一个超级管理员");
        }
        refreshTokens.deleteByUser(id);
        users.invalidateSessions(id, now());
        Locks.requireUpdated(users.update(null, new UpdateWrapper<SysUser>()
                .set("deleted", true).eq("id", id).eq("lock_version", lockVersion)));
        accounts.logicalDelete(user.getPersonId());
    }

    @Transactional
    public UserDetail setStatus(UUID id, Integer lockVersion, String status) {
        guard.check("sys:user:enable");
        SysUser user = require(id);
        requireInCurrentPlant(user);
        protectSuper(user);
        Locks.check(lockVersion, user.getLockVersion());
        if ("DISABLED".equals(status) && id.equals(CurrentContext.userUuid())) {
            throw ApiException.stateConflict("不能停用当前登录账号");
        }
        if ("DISABLED".equals(status) && Boolean.TRUE.equals(user.getSuperAdmin())
                && users.otherActiveSuperAdmins(id) == 0) {
            throw ApiException.stateConflict("不能停用最后一个超级管理员");
        }
        OffsetDateTime now = now();
        if (users.updateStatus(id, status, user.getLockVersion(), now) != 1) {
            throw ApiException.staleVersion();
        }
        if ("DISABLED".equals(status)) {
            refreshTokens.revokeAll(id, now);
        }
        return detail(require(id), null);
    }

    @Transactional
    public UserDetail unlock(UUID id, Integer lockVersion) {
        guard.check("sys:user:update");
        SysUser user = require(id);
        requireInCurrentPlant(user);
        protectSuper(user);
        Locks.check(lockVersion, user.getLockVersion());
        if (users.unlock(id, user.getLockVersion(), now()) != 1) {
            throw ApiException.staleVersion();
        }
        return detail(require(id), null);
    }

    @Transactional
    public PasswordReset resetPassword(UUID id, ResetPassword req) {
        guard.check("sys:user:reset-pwd");
        requireBody(req);
        SysUser user = require(id);
        requireInCurrentPlant(user);
        protectSuper(user);
        Locks.check(req.lockVersion(), user.getLockVersion());
        SecurityPolicy policy = policies.current();
        boolean generated = req.newPassword() == null || req.newPassword().isBlank();
        String password = generated ? PasswordPolicy.generate(user.getUsername(), policy) : req.newPassword();
        PasswordPolicy.check(password, user.getUsername(), policy, "newPassword");
        OffsetDateTime now = now();
        if (users.changePassword(id, encoder.encode(password), true, now) != 1) {
            throw ApiException.notFound("用户");
        }
        refreshTokens.revokeAll(id, now);
        return new PasswordReset(generated ? password : null, true);
    }

    @Transactional
    public UserDetail replacePlants(UUID id, PlantReplace req) {
        guard.check("sys:user:update");
        requireBody(req);
        SysUser user = require(id);
        requireInCurrentPlant(user);
        protectSuper(user);
        if (id.equals(CurrentContext.userUuid())) {
            throw ApiException.permissionDenied("sys:user:grant");
        }
        Locks.check(req.lockVersion(), user.getLockVersion());
        List<AssignmentDraft> submitted = drafts(req.plants());
        UUID defaultPlant;
        if (guard.superAdmin()) {
            defaultPlant = accounts.replaceAssignments(user.getPersonId(), submitted);
        } else {
            UUID current = CurrentContext.requirePlantUuid();
            for (AssignmentDraft draft : submitted) {
                if (draft.plantId() == null || !current.equals(draft.plantId())) {
                    throw ApiException.permissionDenied("sys:user:grant");
                }
            }
            List<AssignmentDraft> merged = new ArrayList<>();
            for (AssignmentView existing : accounts.listAssignments(user.getPersonId())) {
                if (!current.equals(existing.plantId())) {
                    merged.add(new AssignmentDraft(existing.plantId(), existing.defaultPlant(), existing.active(),
                            existing.workshopId(), existing.jobTitle(), existing.roleIds()));
                }
            }
            merged.addAll(submitted);
            defaultPlant = accounts.replaceAssignments(user.getPersonId(), merged, true);
        }
        if (users.replaceDefaultPlant(id, defaultPlant, user.getLockVersion(), now()) != 1) {
            throw ApiException.staleVersion();
        }
        return detail(require(id), null);
    }

    /** Non-super-admins only see users with an active assignment in the current plant. */
    private void requireInCurrentPlant(SysUser user) {
        if (guard.superAdmin()) {
            return;
        }
        UUID plantId = CurrentContext.requirePlantUuid();
        if (NoPlant.ID.equals(plantId) || !accounts.hasActive(user.getPersonId(), plantId)) {
            throw ApiException.notFound("用户");
        }
    }

    private UserDetail detail(SysUser user, String initialPassword) {
        Person person = accounts.require(user.getPersonId());
        List<PlantBindingView> plants = accounts.listAssignments(user.getPersonId()).stream()
                .map(UserService::toView)
                .toList();
        return new UserDetail(user.getId(), user.getUsername(), user.getStatus(),
                Boolean.TRUE.equals(user.getSuperAdmin()), Boolean.TRUE.equals(user.getMustChangePwd()),
                user.getPermVersion() == null ? 0 : user.getPermVersion(), user.getDefaultPlantId(),
                user.getLockVersion() == null ? 0 : user.getLockVersion(),
                new PersonView(person.getId(), person.getCode(), person.getName(), person.getMobile(),
                        person.getEmail(), Boolean.TRUE.equals(person.getEnabled()), person.getRemark(),
                        person.getLockVersion() == null ? 0 : person.getLockVersion()),
                plants, user.getCreatedAt(), user.getUpdatedAt(), initialPassword);
    }

    private static PlantBindingView toView(AssignmentView row) {
        return new PlantBindingView(row.plantId(), row.plantCode(), row.plantName(), row.defaultPlant(),
                row.active(), row.workshopId(), row.jobTitle(), row.roleIds(), row.lockVersion());
    }

    private Map<UUID, Person> loadPeople(List<SysUser> rows) {
        List<UUID> ids = rows.stream().map(SysUser::getPersonId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Person> people = new HashMap<>();
        for (Person person : persons.selectByIds(ids)) {
            people.put(person.getId(), person);
        }
        return people;
    }

    private List<AssignmentDraft> drafts(List<PlantBindingInput> plants) {
        if (plants == null) {
            return List.of();
        }
        return plants.stream()
                .map(row -> new AssignmentDraft(
                        row == null ? null : row.plantId(),
                        row != null && Boolean.TRUE.equals(row.defaultPlant()),
                        row == null || row.active() == null || row.active(),
                        row == null ? null : row.workshopId(),
                        row == null ? null : row.jobTitle(),
                        row == null ? List.of() : row.roleIds()))
                .toList();
    }

    private SysUser require(UUID id) {
        SysUser user = users.selectById(id);
        if (user == null) {
            throw ApiException.notFound("用户");
        }
        return user;
    }

    private void protectSuper(SysUser user) {
        if (Boolean.TRUE.equals(user.getSuperAdmin()) && !guard.superAdmin()) {
            throw ApiException.permissionDenied("sys:user:grant");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static void requireBody(Object body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "请求体不是合法 JSON 或字段类型不符",
                    List.of());
        }
    }

    private static void checkEmail(Errors errors, String email) {
        if (email != null && (email.indexOf('@') < 1 || email.indexOf(' ') >= 0 || email.endsWith("@"))) {
            errors.add("email", ErrorCodes.PATTERN_MISMATCH, "email 格式不正确");
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    static String normalizeUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    static boolean validUsername(String username) {
        if (username == null) {
            return false;
        }
        int length = username.length();
        if (length < 3 || length > 64) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = username.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            if (!ok || (i == 0 && (c == '.' || c == '_' || c == '-'))) {
                return false;
            }
        }
        return true;
    }

    private static ApiException duplicateUsername(String username) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_CODE, "用户名已存在：" + username,
                List.of(new FieldError("username", ErrorCodes.DUPLICATE_CODE, "用户名已存在：" + username)));
    }
}
