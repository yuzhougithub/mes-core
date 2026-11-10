package com.xihua.mes.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.xihua.mes.auth.WriteEndpointGuard;
import com.xihua.mes.support.PgIntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * Menus, roles, per-plant grants, and {@code @RequiresPerm} on embedded PostgreSQL 17.
 */
@TestPropertySource(properties = {
        "mes.auth.mode=jwt",
        "mes.auth.jwt.secret=0123456789abcdef0123456789abcdef"
})
class MenuRolePermMvcTest extends PgIntegrationTest {

    private static final String UOM_MENU = "00000000-0000-4000-8000-00000000f220";
    private static final String UOM_CREATE = "00000000-0000-4000-8000-00000000f222";
    private static final String USER_GRANT = "00000000-0000-4000-8000-00000000f267";
    private static final String ROLE_GRANT = "00000000-0000-4000-8000-00000000f275";
    private static final String PLANT_CREATE = "00000000-0000-4000-8000-00000000f252";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private ObjectProvider<RequestMappingHandlerMapping> mappings;

    @Autowired
    private WriteEndpointGuard writeGuard;

    @Value("${mes.auth.admin.initial-password}")
    private String initialPassword;

    @Test
    void menuCrudDeleteGuardsAndPermCodeUniqueness() throws Exception {
        String token = token(account(null, true));
        JsonNode dir = body(mvc.perform(bearer(post("/api/v1/menus"), token)
                        .content("""
                                {"type":"DIR","name":"质检","sortNo":80}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DIR"))
                .andReturn());
        String dirId = dir.get("id").asString();

        JsonNode menu = body(mvc.perform(bearer(post("/api/v1/menus"), token)
                        .content("""
                                {"type":"MENU","parentId":"%s","name":"检验单","permCode":"qc:order:list",
                                 "path":"/qc/orders","component":"qc/order"}
                                """.formatted(dirId)))
                .andExpect(status().isCreated())
                .andReturn());
        String menuId = menu.get("id").asString();

        JsonNode button = body(mvc.perform(bearer(post("/api/v1/menus"), token)
                        .content("""
                                {"type":"BUTTON","parentId":"%s","name":"新建","permCode":"qc:order:create"}
                                """.formatted(menuId)))
                .andExpect(status().isCreated())
                .andReturn());
        String buttonId = button.get("id").asString();

        mvc.perform(bearer(get("/api/v1/menus/tree"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='质检')]").exists());
        mvc.perform(bearer(get("/api/v1/menus/" + menuId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permCode").value("qc:order:list"));

        mvc.perform(bearer(put("/api/v1/menus/" + menuId), token)
                        .content("""
                                {"type":"MENU","parentId":"%s","name":"检验单-改","permCode":"qc:order:list",
                                 "path":"/qc/orders","component":"qc/order","lockVersion":0}
                                """.formatted(dirId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("检验单-改"));

        mvc.perform(bearer(post("/api/v1/menus"), token)
                        .content("""
                                {"type":"BUTTON","parentId":"%s","name":"重复","permCode":"qc:order:create"}
                                """.formatted(menuId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PERM_CODE"));
        mvc.perform(bearer(post("/api/v1/menus"), token)
                        .content("""
                                {"type":"BUTTON","parentId":"%s","name":"坏编码","permCode":"Not-A-Code"}
                                """.formatted(menuId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(bearer(post("/api/v1/menus"), token)
                        .content("""
                                {"type":"BUTTON","parentId":"%s","name":"挂错","permCode":"qc:order:bad-parent"}
                                """.formatted(dirId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MENU_PARENT_INVALID"));
        mvc.perform(bearer(put("/api/v1/menus/" + dirId), token)
                        .content("""
                                {"type":"DIR","parentId":"%s","name":"质检","lockVersion":0}
                                """.formatted(menuId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MENU_PARENT_INVALID"));

        mvc.perform(bearer(delete("/api/v1/menus/" + menuId + "?lockVersion=1"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MENU_HAS_CHILDREN"));

        mvc.perform(bearer(delete("/api/v1/menus/" + buttonId + "?lockVersion=0"), token))
                .andExpect(status().isNoContent());
        String recreated = body(mvc.perform(bearer(post("/api/v1/menus"), token)
                        .content("""
                                {"type":"BUTTON","parentId":"%s","name":"新建","permCode":"qc:order:create"}
                                """.formatted(menuId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
        mvc.perform(bearer(delete("/api/v1/menus/" + recreated + "?lockVersion=0"), token))
                .andExpect(status().isNoContent());

        JsonNode role = body(mvc.perform(bearer(post("/api/v1/roles"), token)
                        .content("{\"code\":\"QC_HOLD\",\"name\":\"占用角色\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        mvc.perform(bearer(put("/api/v1/roles/" + role.get("id").asString() + "/menus"), token)
                        .content("{\"menuIds\":[\"" + menuId + "\"]}"))
                .andExpect(status().isOk());
        mvc.perform(bearer(delete("/api/v1/menus/" + menuId + "?lockVersion=1"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MENU_IN_USE"))
                .andExpect(jsonPath("$.errors[0].message").value("QC_HOLD"));
    }

    @Test
    void roleCrudAndPerPlantGrants() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        String plantA = admin.plantId();
        String plantB = insertPlant();

        JsonNode created = body(mvc.perform(bearer(post("/api/v1/roles"), adminToken)
                        .content("{\"code\":\"clerk\",\"name\":\"文员\",\"dataScope\":\"PLANT\",\"sortNo\":40}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CLERK"))
                .andExpect(jsonPath("$.builtin").value(false))
                .andReturn());
        String roleId = created.get("id").asString();
        mvc.perform(bearer(put("/api/v1/roles/" + roleId), adminToken)
                        .content("{\"name\":\"文员改\",\"dataScope\":\"SELF\",\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("文员改"))
                .andExpect(jsonPath("$.dataScope").value("SELF"));
        mvc.perform(bearer(get("/api/v1/roles?q=CLERK"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("CLERK"));

        String adminRole = jdbc.queryForObject(
                "SELECT id FROM org_role WHERE code = 'ADMIN' AND NOT deleted", String.class);
        int adminLock = jdbc.queryForObject(
                "SELECT lock_version FROM org_role WHERE id = ?::uuid", Integer.class, adminRole);
        mvc.perform(bearer(delete("/api/v1/roles/" + adminRole + "?lockVersion=" + adminLock), adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUILTIN_PROTECTED"));

        Account clerk = account(plantA, false, roleId);
        String clerkToken = token(clerk);
        mvc.perform(bearer(post("/api/v1/uoms"), clerkToken)
                        .content(uomJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/menus"), adminToken)
                        .content("{\"menuIds\":[\"" + UOM_CREATE + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuIds[0]").value(UOM_CREATE));
        String granted = token(clerk);
        mvc.perform(bearer(post("/api/v1/uoms"), granted).content(uomJson()))
                .andExpect(status().isCreated());

        jdbc.update("""
                INSERT INTO org_person_plant (person_id, plant_id, active, is_default)
                VALUES (?::uuid, ?::uuid, true, false)
                """, clerk.personId(), plantB);
        MvcResult switched = mvc.perform(bearer(post("/api/v1/auth/switch-plant"), granted)
                        .content("{\"plantId\":\"" + plantB + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        mvc.perform(bearer(post("/api/v1/uoms"), body(switched).get("accessToken").asString()).content(uomJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        mvc.perform(bearer(delete("/api/v1/roles/" + roleId + "?lockVersion=1"), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"));
    }

    @Test
    void requiresPermDeniesViewerAndAllowsAdmin() throws Exception {
        Account viewer = account(null, false, "VIEWER");
        mvc.perform(bearer(get("/api/v1/uoms"), token(viewer)))
                .andExpect(status().isOk());
        mvc.perform(bearer(post("/api/v1/menus"), token(viewer))
                        .content("{\"type\":\"DIR\",\"name\":\"越权\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
                .andExpect(jsonPath("$.errors[0].message").value("sys:menu:create"));

        mvc.perform(bearer(post("/api/v1/menus"), token(account(null, true)))
                        .content("{\"type\":\"DIR\",\"name\":\"管理员目录\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void grantChangeTakesEffectOnTheSameToken() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        JsonNode role = body(mvc.perform(bearer(post("/api/v1/roles"), adminToken)
                        .content("{\"code\":\"UOM_MAKER\",\"name\":\"单位维护\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        String roleId = role.get("id").asString();
        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/menus"), adminToken)
                        .content("{\"menuIds\":[\"" + UOM_CREATE + "\"]}"))
                .andExpect(status().isOk());

        Account user = account(admin.plantId(), false, roleId);
        String userToken = token(user);
        int before = jdbc.queryForObject(
                "SELECT perm_version FROM sys_user WHERE id = ?::uuid", Integer.class, user.userId());
        mvc.perform(bearer(post("/api/v1/uoms"), userToken).content(uomJson()))
                .andExpect(status().isCreated());
        mvc.perform(bearer(post("/api/v1/uoms"), userToken).content(uomJson()))
                .andExpect(status().isCreated());

        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/menus"), adminToken)
                        .content("{\"menuIds\":[]}"))
                .andExpect(status().isOk());
        int after = jdbc.queryForObject(
                "SELECT perm_version FROM sys_user WHERE id = ?::uuid", Integer.class, user.userId());
        assertThat(after).isGreaterThan(before);
        mvc.perform(bearer(post("/api/v1/uoms"), userToken).content(uomJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void meReturnsMenuTreeAndPermissionsForTheCurrentPlant() throws Exception {
        Account viewer = account(null, false, "VIEWER");
        mvc.perform(bearer(get("/api/v1/auth/me"), token(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[?(@=='md:material:list')]").exists())
                .andExpect(jsonPath("$.permissions[?(@=='sys:user:create')]").doesNotExist())
                .andExpect(jsonPath("$.menus[?(@.name=='主数据')].children[?(@.name=='物料')].permCode")
                        .value("md:material:list"));

        Account editor = account(null, false, "EDITOR");
        String uom = uom(token(account(editor.plantId(), true)));
        mvc.perform(bearer(post("/api/v1/materials"), token(editor))
                        .content(materialJson("PLANT", uom)))
                .andExpect(status().isCreated());
        mvc.perform(bearer(post("/api/v1/materials"), token(editor))
                        .content(materialJson("SHARED", uom)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].message").value("md:shared:update"));
    }

    @Test
    void forcedPasswordChangeAllowsMeAndBlocksOtherWrites() throws Exception {
        Account user = account(null, false, "VIEWER");
        jdbc.update("UPDATE sys_user SET must_change_pwd = true WHERE id = ?::uuid", user.userId());
        String access = body(login(user.username())).get("accessToken").asString();
        mvc.perform(bearer(get("/api/v1/auth/me"), access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.permissions[?(@=='md:uom:list')]").exists());
        mvc.perform(bearer(post("/api/v1/uoms"), access).content(uomJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void crossPlantAndSelfGrantStayForbidden() throws Exception {
        Account admin = account(null, false, "ADMIN");
        String token = token(admin);
        String otherPlant = insertPlant();
        Account foreign = account(otherPlant, false, "VIEWER");

        mvc.perform(bearer(get("/api/v1/users/" + foreign.userId()), token))
                .andExpect(status().isNotFound());
        mvc.perform(bearer(put("/api/v1/users/" + admin.userId() + "/plants"), token)
                        .content("""
                                {"lockVersion":0,"plants":[{"plantId":"%s","isDefault":true,"roleIds":["%s"]}]}
                                """.formatted(admin.plantId(), roleId("VIEWER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(bearer(put("/api/v1/roles/" + roleId("ADMIN") + "/menus"), token)
                        .content("{\"menuIds\":[\"" + UOM_CREATE + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void cannotGrantAPermissionTheCallerDoesNotHold() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        String grantRole = createRole(adminToken, "EDITOR_GRANT");
        mvc.perform(bearer(put("/api/v1/roles/" + grantRole + "/menus"), adminToken)
                        .content("{\"menuIds\":[\"" + ROLE_GRANT + "\",\"" + UOM_MENU + "\"]}"))
                .andExpect(status().isOk());
        Account caller = account(admin.plantId(), false, grantRole);
        String target = createRole(adminToken, "ESCALATE");
        mvc.perform(bearer(put("/api/v1/roles/" + target + "/menus"), token(caller))
                        .content("{\"menuIds\":[\"" + USER_GRANT + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void cannotAssignARoleBeyondOwnPermissions() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        String grantRole = createRole(adminToken, "PLANTER");
        mvc.perform(bearer(put("/api/v1/roles/" + grantRole + "/menus"), adminToken)
                        .content("{\"menuIds\":[\"" + USER_GRANT + "\",\"" + UOM_MENU + "\"]}"))
                .andExpect(status().isOk());
        Account caller = account(admin.plantId(), false, grantRole);
        Account target = account(admin.plantId(), false);
        mvc.perform(bearer(put("/api/v1/users/" + target.userId() + "/plants"), token(caller))
                        .content("""
                                {"lockVersion":0,"plants":[{"plantId":"%s","isDefault":true,"roleIds":["%s"]}]}
                                """.formatted(admin.plantId(), roleId("ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void disabledParentTurnsOffDescendantPermissions() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        String roleId = createRole(adminToken, "UOM_ONLY");
        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/menus"), adminToken)
                        .content("{\"menuIds\":[\"" + UOM_CREATE + "\"]}"))
                .andExpect(status().isOk());
        Account user = account(admin.plantId(), false, roleId);
        String userToken = token(user);
        mvc.perform(bearer(post("/api/v1/uoms"), userToken).content(uomJson()))
                .andExpect(status().isCreated());

        JsonNode menu = body(mvc.perform(bearer(get("/api/v1/menus/" + UOM_MENU), adminToken))
                .andExpect(status().isOk())
                .andReturn());
        int lock = menu.get("lockVersion").asInt();
        try {
            JsonNode disabled = body(mvc.perform(bearer(put("/api/v1/menus/" + UOM_MENU), adminToken)
                            .content(menuWrite(menu, false, lock)))
                    .andExpect(status().isOk())
                    .andReturn());
            lock = disabled.get("lockVersion").asInt();
            mvc.perform(bearer(post("/api/v1/uoms"), userToken).content(uomJson()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        } finally {
            mvc.perform(bearer(put("/api/v1/menus/" + UOM_MENU), adminToken)
                            .content(menuWrite(menu, true, lock)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void everyRequiresPermCodeIsSeeded() {
        Map<RequestMappingInfo, HandlerMethod> all = new LinkedHashMap<>();
        mappings.orderedStream().forEach(mapping -> all.putAll(mapping.getHandlerMethods()));
        Set<String> declared = WriteEndpointGuard.declaredPerms(all);
        assertThat(declared).isNotEmpty();
        assertThat(jdbc.queryForList(
                "SELECT perm_code FROM sys_menu WHERE perm_code IS NOT NULL AND NOT deleted", String.class))
                .containsAll(declared);
    }

    @Test
    void adminSeedOmitsGlobalWritePerms() {
        Set<String> global = writeGuard.globalPermCodes();
        assertThat(global).contains(
                "org:plant:create", "org:plant:update", "org:plant:delete",
                "sys:menu:create", "sys:menu:update", "sys:menu:delete",
                "sys:role:create", "sys:role:update", "sys:role:delete",
                "sys:role:grant");
        List<String> held = jdbc.queryForList("""
                SELECT m.perm_code
                  FROM sys_role_menu rm
                  JOIN org_role r ON r.id = rm.role_id
                  JOIN sys_menu m ON m.id = rm.menu_id
                 WHERE r.code = 'ADMIN' AND NOT r.deleted
                   AND m.perm_code IS NOT NULL
                """, String.class);
        assertThat(held).doesNotContainAnyElementsOf(global);
        assertThat(held).contains(
                "org:plant:list", "org:plant:view",
                "sys:menu:list", "sys:menu:view",
                "sys:role:list", "sys:role:view",
                "sys:user:create", "md:material:create");
    }

    @Test
    void meHidesGlobalPermCodesFromNonSuperadmins() throws Exception {
        Set<String> global = writeGuard.globalPermCodes();
        Account admin = account(null, false, "ADMIN");
        JsonNode me = body(mvc.perform(bearer(get("/api/v1/auth/me"), token(admin)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(texts(me.get("permissions")))
                .doesNotContainAnyElementsOf(global)
                .contains("org:plant:list", "sys:user:create", "sys:role:view", "md:material:create");
        assertThat(permCodes(me.get("menus"))).doesNotContainAnyElementsOf(global);

        mvc.perform(bearer(get("/api/v1/plants"), token(admin)))
                .andExpect(status().isOk());
        mvc.perform(bearer(get("/api/v1/menus/tree"), token(admin)))
                .andExpect(status().isOk());
        mvc.perform(bearer(get("/api/v1/roles"), token(admin)))
                .andExpect(status().isOk());

        JsonNode superMe = body(mvc.perform(bearer(get("/api/v1/auth/me"), token(account(admin.plantId(), true))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(texts(superMe.get("permissions"))).containsAll(global);
        assertThat(permCodes(superMe.get("menus"))).containsAll(global);
    }

    @Test
    void meHidesGlobalPermCodesEvenWhenTheRoleHoldsThem() throws Exception {
        Account admin = account(null, true);
        String adminToken = token(admin);
        String roleId = createRole(adminToken, "GLOBAL_HOLD");
        mvc.perform(bearer(put("/api/v1/roles/" + roleId + "/menus"), adminToken)
                        .content("{\"menuIds\":[\"" + PLANT_CREATE + "\",\"" + UOM_MENU + "\"]}"))
                .andExpect(status().isOk());
        Account holder = account(admin.plantId(), false, roleId);
        JsonNode me = body(mvc.perform(bearer(get("/api/v1/auth/me"), token(holder)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(texts(me.get("permissions")))
                .contains("md:uom:list")
                .doesNotContain("org:plant:create")
                .doesNotContainAnyElementsOf(writeGuard.globalPermCodes());
        assertThat(permCodes(me.get("menus")))
                .contains("md:uom:list")
                .doesNotContain("org:plant:create");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("globalResourceWrites")
    void plantAdminIsForbiddenOnGlobalResourceWrites(String method, String pattern) throws Exception {
        Account admin = account(null, false, "ADMIN");
        String path = pattern.replaceAll("\\{[^}/]+}", UUID.randomUUID().toString());
        MockHttpServletRequestBuilder request = switch (method) {
            case "POST" -> post(path).content("{}");
            case "PUT" -> put(path).content("{}");
            case "PATCH" -> patch(path).content("{}");
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        mvc.perform(bearer(request, token(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void globalResourceWriteCasesCoverEveryEndpoint() {
        Map<RequestMappingInfo, HandlerMethod> all = new LinkedHashMap<>();
        mappings.orderedStream().forEach(mapping -> all.putAll(mapping.getHandlerMethods()));
        List<String> discovered = WriteEndpointGuard.globalResourceWrites(all);
        List<String> cases = globalResourceWrites()
                .map(args -> args.get()[0] + " " + args.get()[1])
                .toList();
        assertThat(discovered).isNotEmpty();
        assertThat(cases).containsExactlyInAnyOrderElementsOf(discovered);
    }

    static Stream<Arguments> globalResourceWrites() {
        return Stream.of(
                Arguments.of("POST", "/api/v1/plants"),
                Arguments.of("PUT", "/api/v1/plants/{id}"),
                Arguments.of("DELETE", "/api/v1/plants/{id}"),
                Arguments.of("POST", "/api/v1/menus"),
                Arguments.of("PUT", "/api/v1/menus/{id}"),
                Arguments.of("DELETE", "/api/v1/menus/{id}"),
                Arguments.of("POST", "/api/v1/roles"),
                Arguments.of("PUT", "/api/v1/roles/{id}"),
                Arguments.of("DELETE", "/api/v1/roles/{id}"),
                Arguments.of("PUT", "/api/v1/roles/{id}/menus"),
                Arguments.of("PUT", "/api/v1/roles/{id}/data-scope"),
                Arguments.of("POST", "/api/v1/posts"),
                Arguments.of("PUT", "/api/v1/posts/{id}"),
                Arguments.of("DELETE", "/api/v1/posts/{id}"));
    }

    @Test
    void menuCycleIsRejectedByTheDatabase() {
        UUID left = UUID.randomUUID();
        UUID right = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    jdbc.update("INSERT INTO sys_menu (id, type, name) VALUES (?, 'DIR', ?)", left, "环A");
                    jdbc.update("INSERT INTO sys_menu (id, parent_id, type, name) VALUES (?, ?, 'DIR', ?)",
                            right, left, "环B");
                    jdbc.update("UPDATE sys_menu SET parent_id = ? WHERE id = ?", right, left);
                }));
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asString()));
        return values;
    }

    private static List<String> permCodes(JsonNode menus) {
        List<String> codes = new ArrayList<>();
        if (menus == null || !menus.isArray()) {
            return codes;
        }
        for (JsonNode node : menus) {
            if (node.hasNonNull("permCode")) {
                codes.add(node.get("permCode").asString());
            }
            codes.addAll(permCodes(node.get("children")));
        }
        return codes;
    }

    private String createRole(String token, String code) throws Exception {
        return body(mvc.perform(bearer(post("/api/v1/roles"), token)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
    }

    private static String menuWrite(JsonNode menu, boolean enabled, int lockVersion) {
        String parent = menu.get("parentId").isNull() ? "null" : "\"" + menu.get("parentId").asString() + "\"";
        return """
                {"type":"%s","parentId":%s,"name":"%s","permCode":"%s","path":"%s","component":"%s",
                 "enabled":%s,"lockVersion":%d}
                """.formatted(menu.get("type").asString(), parent, menu.get("name").asString(),
                menu.get("permCode").asString(), menu.get("path").asString(), menu.get("component").asString(),
                enabled, lockVersion);
    }

    private String uom(String token) throws Exception {
        return body(mvc.perform(bearer(post("/api/v1/uoms"), token).content(uomJson()))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
    }

    private static String uomJson() {
        return "{\"code\":\"" + uniq("U") + "\",\"name\":\"件\",\"decimals\":0}";
    }

    private static String materialJson(String scope, String uomId) {
        return """
                {"scope":"%s","code":"%s","name":"壳体","materialType":"SEMI","uomId":"%s"}
                """.formatted(scope, uniq("MAT"), uomId);
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
