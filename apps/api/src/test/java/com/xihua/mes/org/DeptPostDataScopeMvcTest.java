package com.xihua.mes.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.xihua.mes.support.PgIntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * F3: department tree, posts, person assignment, six data scopes, plant isolation, global writes.
 */
@TestPropertySource(properties = {
        "mes.auth.mode=jwt",
        "mes.auth.jwt.secret=0123456789abcdef0123456789abcdef"
})
class DeptPostDataScopeMvcTest extends PgIntegrationTest {

    private static final String USER_LIST = "00000000-0000-4000-8000-00000000f260";
    private static final String USER_VIEW = "00000000-0000-4000-8000-00000000f261";
    private static final String USER_UPDATE = "00000000-0000-4000-8000-00000000f263";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Value("${mes.auth.admin.initial-password}")
    private String initialPassword;

    @Test
    void departmentTreeCrudCycleAndSamePlant() throws Exception {
        Account admin = account(null, true);
        String token = token(admin);
        JsonNode root = body(mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content(deptJson(uniq("D"), "根", null)))
                .andExpect(status().isCreated())
                .andReturn());
        String rootId = root.get("id").asString();
        assertThat(root.get("ancestors").asString()).isEqualTo("/" + rootId + "/");
        assertThat(root.get("plantId").asString()).isEqualTo(admin.plantId());

        JsonNode child = body(mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content(deptJson(uniq("D"), "子", rootId)))
                .andExpect(status().isCreated())
                .andReturn());
        String childId = child.get("id").asString();
        assertThat(child.get("ancestors").asString()).isEqualTo("/" + rootId + "/" + childId + "/");

        JsonNode grand = body(mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content(deptJson(uniq("D"), "孙", childId)))
                .andExpect(status().isCreated())
                .andReturn());
        String grandId = grand.get("id").asString();

        mvc.perform(bearer(get("/api/v1/depts/tree"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + rootId + "')].children[?(@.id=='" + childId + "')]").exists());

        JsonNode moved = body(mvc.perform(bearer(post("/api/v1/depts/" + childId + "/move"), token)
                        .content("{\"parentId\":null,\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(moved.get("parentId").isNull()).isTrue();
        assertThat(moved.get("ancestors").asString()).isEqualTo("/" + childId + "/");
        assertThat(ancestors(grandId)).isEqualTo("/" + childId + "/" + grandId + "/");

        mvc.perform(bearer(post("/api/v1/depts/" + childId + "/move"), token)
                        .content("{\"parentId\":\"" + grandId + "\",\"lockVersion\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("parentId"));

        String otherPlant = insertPlant();
        String foreignDept = insertDept(otherPlant, uniq("D"));
        mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content(deptJson(uniq("D"), "跨厂", foreignDept)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("CROSS_PLANT_REFERENCE"));

        mvc.perform(bearer(put("/api/v1/depts/" + rootId), token)
                        .content("""
                                {"name":"根-改","parentId":"%s","enabled":false,"sortNo":3,"lockVersion":0}
                                """.formatted(rootId)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(bearer(put("/api/v1/depts/" + rootId), token)
                        .content("""
                                {"name":"根-改","parentId":null,"enabled":false,"sortNo":3,"lockVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("根-改"))
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content(deptJson(root.get("code").asString(), "重复", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));

        mvc.perform(bearer(delete("/api/v1/depts/" + childId + "?lockVersion=1"), token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEPT_NOT_EMPTY"));
        jdbc.update("UPDATE org_person SET dept_id = ?::uuid WHERE id = ?::uuid", rootId, admin.personId());
        mvc.perform(bearer(delete("/api/v1/depts/" + rootId + "?lockVersion=1"), token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEPT_NOT_EMPTY"));
        jdbc.update("UPDATE org_person SET dept_id = NULL WHERE id = ?::uuid", admin.personId());

        mvc.perform(bearer(post("/api/v1/depts/" + grandId + "/move"), token)
                        .content("{\"parentId\":null,\"lockVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(bearer(delete("/api/v1/depts/" + childId + "?lockVersion=1"), token))
                .andExpect(status().isNoContent());
    }

    @Test
    void postCrud() throws Exception {
        String token = token(account(null, true));
        String code = uniq("POST");
        JsonNode created = body(mvc.perform(bearer(post("/api/v1/posts"), token)
                        .content("{\"code\":\"" + code + "\",\"name\":\"操作工\",\"sortNo\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andReturn());
        String id = created.get("id").asString();
        mvc.perform(bearer(get("/api/v1/posts?q=" + code), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("操作工"));
        mvc.perform(bearer(put("/api/v1/posts/" + id), token)
                        .content("{\"name\":\"操作工-改\",\"enabled\":false,\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("操作工-改"))
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(bearer(post("/api/v1/posts"), token)
                        .content("{\"code\":\"" + code + "\",\"name\":\"重复\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        mvc.perform(bearer(delete("/api/v1/posts/" + id + "?lockVersion=1"), token))
                .andExpect(status().isNoContent());
        mvc.perform(bearer(get("/api/v1/posts?q=" + code), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void personCanBelongToADepartmentAndPosts() throws Exception {
        Account admin = account(null, true);
        String token = token(admin);
        String deptId = body(mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content(deptJson(uniq("D"), "装配", null)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        String postId = body(mvc.perform(bearer(post("/api/v1/posts"), token)
                        .content("{\"code\":\"" + uniq("POST") + "\",\"name\":\"班长\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        String otherPost = body(mvc.perform(bearer(post("/api/v1/posts"), token)
                        .content("{\"code\":\"" + uniq("POST") + "\",\"name\":\"质检\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();

        String code = uniq("U");
        JsonNode created = body(mvc.perform(bearer(post("/api/v1/users"), token)
                        .content("""
                                {"code":"%s","name":"张三","password":"Init@Pass1","deptId":"%s","postIds":["%s"],
                                 "plants":[{"plantId":"%s","isDefault":true}]}
                                """.formatted(code, deptId, postId, admin.plantId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person.deptId").value(deptId))
                .andExpect(jsonPath("$.person.postIds[0]").value(postId))
                .andReturn());
        String userId = created.get("id").asString();
        int before = jdbc.queryForObject(
                "SELECT perm_version FROM sys_user WHERE id = ?::uuid", Integer.class, userId);

        mvc.perform(bearer(put("/api/v1/users/" + userId), token)
                        .content("""
                                {"username":"%s","name":"张三","lockVersion":0,
                                 "org":{"deptId":null,"postIds":["%s"]}}
                                """.formatted(created.get("username").asString(), otherPost)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person.deptId").value(nullValue()))
                .andExpect(jsonPath("$.person.postIds[0]").value(otherPost));
        int after = jdbc.queryForObject(
                "SELECT perm_version FROM sys_user WHERE id = ?::uuid", Integer.class, userId);
        assertThat(after).isGreaterThan(before);

        mvc.perform(bearer(delete("/api/v1/posts/" + otherPost + "?lockVersion=0"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"));

        String otherPlant = insertPlant();
        String foreignDept = insertDept(otherPlant, uniq("D"));
        mvc.perform(bearer(put("/api/v1/users/" + userId), token)
                        .content("""
                                {"username":"%s","name":"张三","lockVersion":1,
                                 "org":{"deptId":"%s","postIds":[]}}
                                """.formatted(created.get("username").asString(), foreignDept)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("CROSS_PLANT_REFERENCE"));
    }

    @Test
    void eachDataScopeFiltersListAndGet() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        String root = dept(adminToken, uniq("D"), "根", null);
        String child = dept(adminToken, uniq("D"), "子", root);
        String grand = dept(adminToken, uniq("D"), "孙", child);
        String sibling = dept(adminToken, uniq("D"), "兄", root);

        String roleId = body(mvc.perform(bearer(post("/api/v1/roles"), adminToken)
                        .content("{\"code\":\"" + roleCode() + "\",\"name\":\"范围\",\"dataScope\":\"DEPT\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/menus"), adminToken)
                        .content("{\"menuIds\":[\"" + USER_LIST + "\",\"" + USER_VIEW + "\",\"" + USER_UPDATE + "\"]}"))
                .andExpect(status().isOk());

        Account caller = account(admin.plantId(), false, roleId);
        place(caller, child, admin.userId());
        Account inChild = account(admin.plantId(), false);
        place(inChild, child, admin.userId());
        Account inGrand = account(admin.plantId(), false);
        place(inGrand, grand, admin.userId());
        Account inRoot = account(admin.plantId(), false);
        place(inRoot, root, admin.userId());
        Account inSibling = account(admin.plantId(), false);
        place(inSibling, sibling, admin.userId());
        Account noDept = account(admin.plantId(), false);
        place(noDept, null, admin.userId());
        Account madeByCaller = account(admin.plantId(), false);
        place(madeByCaller, null, caller.userId());
        Account otherPlantUser = account(insertPlant(), false);

        String token = token(caller);
        assertUsernames(token, caller.username(), inChild.username());
        mvc.perform(bearer(get("/api/v1/users/" + inGrand.userId()), token)).andExpect(status().isNotFound());
        mvc.perform(bearer(get("/api/v1/users/" + inChild.userId()), token)).andExpect(status().isOk());
        mvc.perform(bearer(put("/api/v1/users/" + inGrand.userId()), token)
                        .content("{\"username\":\"nobody\",\"name\":\"x\",\"lockVersion\":0}"))
                .andExpect(status().isNotFound());

        setScope(roleId, "DEPT_AND_CHILD");
        assertUsernames(token, caller.username(), inChild.username(), inGrand.username());

        setScope(roleId, "SELF");
        assertUsernames(token, caller.username());
        mvc.perform(bearer(get("/api/v1/users/" + caller.userId()), token)).andExpect(status().isOk());
        mvc.perform(bearer(get("/api/v1/users/" + madeByCaller.userId()), token)).andExpect(status().isNotFound());

        setScope(roleId, "CUSTOM");
        jdbc.update("DELETE FROM sys_role_data_dept WHERE role_id = ?::uuid", roleId);
        jdbc.update("INSERT INTO sys_role_data_dept (role_id, dept_id) VALUES (?::uuid, ?::uuid)", roleId, root);
        jdbc.update("INSERT INTO sys_role_data_dept (role_id, dept_id) VALUES (?::uuid, ?::uuid)", roleId, sibling);
        assertUsernames(token, inRoot.username(), inSibling.username());

        setScope(roleId, "PLANT");
        assertUsernames(token, admin.username(), caller.username(), inChild.username(), inGrand.username(),
                inRoot.username(), inSibling.username(), noDept.username(), madeByCaller.username());
        assertThat(usernames(token)).doesNotContain(otherPlantUser.username());

        setScope(roleId, "ALL");
        assertUsernames(token, admin.username(), caller.username(), inChild.username(), inGrand.username(),
                inRoot.username(), inSibling.username(), noDept.username(), madeByCaller.username());
        assertThat(usernames(token)).doesNotContain(otherPlantUser.username());

        String selfRole = body(mvc.perform(bearer(post("/api/v1/roles"), adminToken)
                        .content("{\"code\":\"" + roleCode() + "\",\"name\":\"本人\",\"dataScope\":\"SELF\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        setScope(roleId, "CUSTOM");
        jdbc.update("DELETE FROM sys_role_data_dept WHERE role_id = ?::uuid", roleId);
        jdbc.update("INSERT INTO sys_role_data_dept (role_id, dept_id) VALUES (?::uuid, ?::uuid)", roleId, sibling);
        jdbc.update("""
                INSERT INTO org_person_plant_role (person_plant_id, role_id)
                SELECT id, ?::uuid FROM org_person_plant
                 WHERE person_id = ?::uuid AND plant_id = ?::uuid
                """, selfRole, caller.personId(), caller.plantId());
        assertUsernames(token, caller.username(), inSibling.username());
    }

    @Test
    void plantIsolationStillAppliesBesideDataScope() throws Exception {
        Account admin = account(null, true);
        String token = token(admin);
        String local = dept(token, uniq("D"), "本厂", null);
        String shared = body(mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content("{\"code\":\"" + uniq("D") + "\",\"name\":\"集团部\",\"shared\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plantId").value(nullValue()))
                .andReturn()).get("id").asString();
        String otherPlant = insertPlant();
        String foreign = insertDept(otherPlant, uniq("D"));
        String localWorkshop = insertWorkshop(admin.plantId(), uniq("W"));
        String foreignWorkshop = insertWorkshop(otherPlant, uniq("W"));
        insertLine(admin.plantId(), localWorkshop, uniq("L"));
        insertLine(otherPlant, foreignWorkshop, uniq("L"));

        mvc.perform(bearer(get("/api/v1/depts/tree"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + local + "')]").exists())
                .andExpect(jsonPath("$[?(@.id=='" + shared + "')]").exists())
                .andExpect(jsonPath("$[?(@.id=='" + foreign + "')]").doesNotExist());

        jdbc.update("""
                INSERT INTO org_person_plant (person_id, plant_id, active, is_default)
                VALUES (?::uuid, ?::uuid, true, false)
                """, admin.personId(), otherPlant);
        String switched = body(mvc.perform(bearer(post("/api/v1/auth/switch-plant"), token)
                        .content("{\"plantId\":\"" + otherPlant + "\"}"))
                .andExpect(status().isOk())
                .andReturn()).get("accessToken").asString();
        mvc.perform(bearer(get("/api/v1/depts/tree"), switched))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + foreign + "')]").exists())
                .andExpect(jsonPath("$[?(@.id=='" + shared + "')]").exists())
                .andExpect(jsonPath("$[?(@.id=='" + local + "')]").doesNotExist());

        String roleId = body(mvc.perform(bearer(post("/api/v1/roles"), token)
                        .content("{\"code\":\"" + roleCode() + "\",\"name\":\"全厂\",\"dataScope\":\"PLANT\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        Account caller = account(admin.plantId(), false, roleId);
        jdbc.update("""
                INSERT INTO org_person_plant (person_id, plant_id, active, is_default)
                VALUES (?::uuid, ?::uuid, true, false)
                """, caller.personId(), otherPlant);
        String callerToken = token(caller);
        JsonNode plantTree = body(mvc.perform(bearer(get("/api/v1/org/tree"), callerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("GROUP"))
                .andExpect(jsonPath("$.name").value("集团"))
                .andReturn());
        assertThat(plantIds(plantTree)).containsExactly(caller.plantId());
        assertThat(workshopIds(plantTree)).contains(localWorkshop).doesNotContain(foreignWorkshop);

        setScope(roleId, "ALL");
        JsonNode allTree = body(mvc.perform(bearer(get("/api/v1/org/tree"), callerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(plantIds(allTree)).containsExactlyInAnyOrder(caller.plantId(), otherPlant);
        assertThat(workshopIds(allTree)).contains(localWorkshop, foreignWorkshop);

        JsonNode superTree = body(mvc.perform(bearer(get("/api/v1/org/tree"), token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(workshopIds(superTree)).contains(localWorkshop, foreignWorkshop);
    }

    @Test
    void nonSuperAdminCannotWriteGlobalResources() throws Exception {
        Account admin = account(null, false, "ADMIN");
        String token = token(admin);
        mvc.perform(bearer(post("/api/v1/plants"), token)
                        .content("{\"code\":\"" + uniq("P") + "\",\"name\":\"越权厂\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(bearer(post("/api/v1/menus"), token)
                        .content("{\"type\":\"DIR\",\"name\":\"越权目录\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(bearer(post("/api/v1/roles"), token)
                        .content("{\"code\":\"" + uniq("R") + "\",\"name\":\"越权角色\"}"))
                .andExpect(status().isForbidden());
        String roleId = roleId("ADMIN");
        mvc.perform(bearer(put("/api/v1/roles/" + roleId), token)
                        .content("{\"name\":\"改名\",\"lockVersion\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/menus"), token)
                        .content("{\"menuIds\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/data-scope"), token)
                        .content("{\"dataScope\":\"ALL\",\"deptIds\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content(deptJson(uniq("D"), "本厂部门", null)))
                .andExpect(status().isCreated());
        mvc.perform(bearer(post("/api/v1/posts"), token)
                        .content("{\"code\":\"" + uniq("POST") + "\",\"name\":\"岗位\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void putDeptOmitsParentAndExplicitParentMoves() throws Exception {
        String token = token(account(null, true));
        String root = dept(token, uniq("D"), "根", null);
        String child = dept(token, uniq("D"), "子", root);
        mvc.perform(bearer(put("/api/v1/depts/" + child), token)
                        .content("{\"name\":\"子-改名\",\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("子-改名"))
                .andExpect(jsonPath("$.parentId").value(root));
        mvc.perform(bearer(put("/api/v1/depts/" + child), token)
                        .content("{\"name\":\"子-到根\",\"parentId\":null,\"lockVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(nullValue()));
    }

    @Test
    void plantAdminWritesOnlyCurrentPlantDeptsAndAssignsExistingPosts() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        String shared = body(mvc.perform(bearer(post("/api/v1/depts"), adminToken)
                        .content("{\"code\":\"" + uniq("D") + "\",\"name\":\"集团部\",\"shared\":true}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        String postId = body(mvc.perform(bearer(post("/api/v1/posts"), adminToken)
                        .content("{\"code\":\"" + uniq("POST") + "\",\"name\":\"班长\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        Account local = account(admin.plantId(), false);
        Account plantAdmin = account(admin.plantId(), false, "ADMIN");
        String token = token(plantAdmin);

        mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content("{\"code\":\"" + uniq("D") + "\",\"name\":\"越权集团\",\"shared\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(bearer(put("/api/v1/depts/" + shared), token)
                        .content("{\"name\":\"改集团\",\"lockVersion\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(delete("/api/v1/depts/" + shared + "?lockVersion=0"), token))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(post("/api/v1/depts/" + shared + "/move"), token)
                        .content("{\"parentId\":null,\"lockVersion\":0}"))
                .andExpect(status().isForbidden());

        String own = body(mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content(deptJson(uniq("D"), "本厂", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plantId").value(admin.plantId()))
                .andReturn()).get("id").asString();
        mvc.perform(bearer(put("/api/v1/depts/" + own), token)
                        .content("{\"name\":\"本厂-改\",\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("本厂-改"));

        mvc.perform(bearer(put("/api/v1/posts/" + postId), token)
                        .content("{\"name\":\"改岗位\",\"lockVersion\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(delete("/api/v1/posts/" + postId + "?lockVersion=0"), token))
                .andExpect(status().isForbidden());

        mvc.perform(bearer(put("/api/v1/users/" + local.userId()), token)
                        .content("""
                                {"username":"%s","name":"测试","lockVersion":0,"org":{"postIds":["%s"]}}
                                """.formatted(local.username(), postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person.postIds[0]").value(postId));
    }

    @Test
    void deptAndChildUserCannotChangeOwnDepartment() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        String mine = dept(adminToken, uniq("D"), "本部", null);
        String below = dept(adminToken, uniq("D"), "下级", mine);
        String roleId = scopedRole(adminToken, "DEPT_AND_CHILD");
        Account caller = account(admin.plantId(), false, roleId);
        place(caller, mine, admin.userId());

        mvc.perform(bearer(put("/api/v1/users/" + caller.userId()), token(caller))
                        .content("""
                                {"username":"%s","name":"测试","lockVersion":0,"org":{"deptId":"%s"}}
                                """.formatted(caller.username(), below)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(jdbc.queryForObject(
                "SELECT dept_id::text FROM org_person WHERE id = ?::uuid", String.class, caller.personId()))
                .isEqualTo(mine);
    }

    @Test
    void cannotAssignPersonOutsideDataScope() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        String mine = dept(adminToken, uniq("D"), "本部", null);
        String below = dept(adminToken, uniq("D"), "下级", mine);
        String outside = dept(adminToken, uniq("D"), "外部门", null);
        String roleId = scopedRole(adminToken, "DEPT_AND_CHILD");
        Account caller = account(admin.plantId(), false, roleId);
        Account colleague = account(admin.plantId(), false);
        place(caller, mine, admin.userId());
        place(colleague, mine, admin.userId());
        String callerToken = token(caller);

        mvc.perform(bearer(put("/api/v1/users/" + colleague.userId()), callerToken)
                        .content("""
                                {"username":"%s","name":"测试","lockVersion":0,"org":{"deptId":"%s"}}
                                """.formatted(colleague.username(), outside)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(bearer(put("/api/v1/users/" + colleague.userId()), callerToken)
                        .content("""
                                {"username":"%s","name":"测试","lockVersion":0,"org":{"deptId":"%s"}}
                                """.formatted(colleague.username(), below)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person.deptId").value(below));
    }

    @Test
    void leaderMustWorkInTheCurrentPlant() throws Exception {
        Account admin = account(null, true);
        String token = token(admin);
        Account other = account(insertPlant(), false);
        mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content("""
                                {"code":"%s","name":"装配","leaderPersonId":"%s"}
                                """.formatted(uniq("D"), other.personId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("leaderPersonId"));
        mvc.perform(bearer(post("/api/v1/depts"), token)
                        .content("""
                                {"code":"%s","name":"装配","leaderPersonId":"%s"}
                                """.formatted(uniq("D"), admin.personId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaderPersonId").value(admin.personId()));
    }

    @Test
    void customDeptRejectsOtherPlantsAndSkipsDeleted() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        String local = dept(adminToken, uniq("D"), "本厂", null);
        String otherPlant = insertPlant();
        String foreign = insertDept(otherPlant, uniq("D"));
        String roleId = body(mvc.perform(bearer(post("/api/v1/roles"), adminToken)
                        .content("{\"code\":\"" + roleCode() + "\",\"name\":\"自定义\",\"dataScope\":\"PLANT\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/data-scope"), adminToken)
                        .content("{\"dataScope\":\"CUSTOM\",\"deptIds\":[\"" + foreign + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CROSS_PLANT_REFERENCE"));
        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/menus"), adminToken)
                        .content("{\"menuIds\":[\"" + USER_LIST + "\",\"" + USER_VIEW + "\"]}"))
                .andExpect(status().isOk());
        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/data-scope"), adminToken)
                        .content("{\"dataScope\":\"CUSTOM\",\"deptIds\":[\"" + local + "\"]}"))
                .andExpect(status().isOk());

        Account caller = account(admin.plantId(), false, roleId);
        Account inside = account(admin.plantId(), false);
        place(caller, local, admin.userId());
        place(inside, local, admin.userId());
        String callerToken = token(caller);
        assertUsernames(callerToken, caller.username(), inside.username());

        jdbc.update("UPDATE org_dept SET deleted = true WHERE id = ?::uuid", local);
        assertUsernames(callerToken);
    }

    private String scopedRole(String adminToken, String dataScope) throws Exception {
        String roleId = body(mvc.perform(bearer(post("/api/v1/roles"), adminToken)
                        .content("{\"code\":\"" + roleCode() + "\",\"name\":\"范围\",\"dataScope\":\"" + dataScope + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/menus"), adminToken)
                        .content("{\"menuIds\":[\"" + USER_LIST + "\",\"" + USER_VIEW + "\",\"" + USER_UPDATE + "\"]}"))
                .andExpect(status().isOk());
        return roleId;
    }

    private void assertUsernames(String token, String... expected) throws Exception {
        assertThat(usernames(token)).containsExactlyInAnyOrder(expected);
    }

    private Set<String> usernames(String token) throws Exception {
        JsonNode page = body(mvc.perform(bearer(get("/api/v1/users?size=200"), token))
                .andExpect(status().isOk())
                .andReturn());
        Set<String> names = new HashSet<>();
        for (JsonNode item : page.get("items")) {
            names.add(item.get("username").asString());
        }
        assertThat(page.get("total").asInt()).isEqualTo(names.size());
        return names;
    }

    private static Set<String> plantIds(JsonNode tree) {
        Set<String> ids = new HashSet<>();
        for (JsonNode plant : tree.get("children")) {
            ids.add(plant.get("id").asString());
        }
        return ids;
    }

    private static Set<String> workshopIds(JsonNode tree) {
        Set<String> ids = new HashSet<>();
        for (JsonNode plant : tree.get("children")) {
            for (JsonNode workshop : plant.get("children")) {
                ids.add(workshop.get("id").asString());
            }
        }
        return ids;
    }

    private String dept(String token, String code, String name, String parentId) throws Exception {
        return body(mvc.perform(bearer(post("/api/v1/depts"), token).content(deptJson(code, name, parentId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
    }

    private static String roleCode() {
        return "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private static String deptJson(String code, String name, String parentId) {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        return "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"parentId\":" + parent + "}";
    }

    private void setScope(String roleId, String scope) {
        jdbc.update("UPDATE org_role SET data_scope = ? WHERE id = ?::uuid", scope, roleId);
    }

    private void place(Account account, String deptId, String createdBy) {
        jdbc.update("UPDATE org_person SET dept_id = ?::uuid WHERE id = ?::uuid", deptId, account.personId());
        jdbc.update("UPDATE sys_user SET created_by = ?::uuid WHERE id = ?::uuid", createdBy, account.userId());
        jdbc.update("UPDATE org_person SET created_by = ?::uuid WHERE id = ?::uuid", createdBy, account.personId());
    }

    private String ancestors(String deptId) {
        return jdbc.queryForObject(
                "SELECT ancestors FROM org_dept WHERE id = ?::uuid", String.class, deptId);
    }

    private String insertDept(String plantId, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO org_dept (id, plant_id, code, name, ancestors)
                VALUES (?, ?::uuid, ?, '外厂', '/' || ? || '/')
                """, id, plantId, code, id);
        return id.toString();
    }

    private String insertWorkshop(String plantId, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO org_workshop (id, plant_id, code, name) VALUES (?, ?::uuid, ?, '车间')",
                id, plantId, code);
        return id.toString();
    }

    private void insertLine(String plantId, String workshopId, String code) {
        jdbc.update("""
                INSERT INTO org_work_center (id, plant_id, workshop_id, code, name, work_center_type)
                VALUES (?, ?::uuid, ?::uuid, ?, '产线', 'LINE')
                """, UUID.randomUUID(), plantId, workshopId, code);
    }

    private String token(Account account) throws Exception {
        return body(login(account.username())).get("accessToken").asString();
    }

    private MvcResult login(String username) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + initialPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Account account(String plantId, boolean superAdmin) {
        return account(plantId, superAdmin, null);
    }

    private Account account(String plantId, boolean superAdmin, String roleIdOrCode) {
        String plant = plantId == null ? insertPlant() : plantId;
        UUID personId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String hash = new BCryptPasswordEncoder(10).encode(initialPassword);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO org_person (id, code, name) VALUES (?, ?, ?)",
                    personId, username.toUpperCase(), "测试");
            jdbc.update("""
                    INSERT INTO sys_user
                        (id, username, person_id, password_hash, status, super_admin, must_change_pwd,
                         default_plant_id, perm_version)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, false, ?, 0)
                    """, userId, username, personId, hash, superAdmin, UUID.fromString(plant));
            jdbc.update("""
                    INSERT INTO org_person_plant (id, person_id, plant_id, active, is_default)
                    VALUES (?, ?, ?, true, true)
                    """, assignmentId, personId, UUID.fromString(plant));
            if (roleIdOrCode != null) {
                UUID roleUuid = null;
                try {
                    roleUuid = UUID.fromString(roleIdOrCode);
                } catch (IllegalArgumentException ignored) {
                    roleUuid = null;
                }
                if (roleUuid != null) {
                    jdbc.update("""
                            INSERT INTO org_person_plant_role (person_plant_id, role_id)
                            SELECT ?, id FROM org_role WHERE id = ? AND NOT deleted
                            """, assignmentId, roleUuid);
                } else {
                    jdbc.update("""
                            INSERT INTO org_person_plant_role (person_plant_id, role_id)
                            SELECT ?, id FROM org_role WHERE code = ? AND NOT deleted
                            """, assignmentId, roleIdOrCode);
                }
            }
        });
        return new Account(username, userId.toString(), personId.toString(), plant);
    }

    private String insertPlant() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO org_plant (id, code, name) VALUES (?, ?, ?)", id, uniq("P"), "工厂");
        return id.toString();
    }

    private String roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id::text FROM org_role WHERE code = ? AND NOT deleted", String.class, code);
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private record Account(String username, String userId, String personId, String plantId) {
    }
}
