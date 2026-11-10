package com.xihua.mes.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.xihua.mes.support.PgIntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * JWT mode: login, lockout, refresh rotation, logout, plant switch, password policy, user CRUD.
 */
@TestPropertySource(properties = {
        "mes.auth.mode=jwt",
        "mes.auth.jwt.secret=0123456789abcdef0123456789abcdef"
})
class AuthFlowMvcTest extends PgIntegrationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "ChangeMe@1";
    private static String passwordHash;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactions;

    @BeforeAll
    static void hashPassword() {
        passwordHash = new BCryptPasswordEncoder(10).encode(PASSWORD);
    }

    @Test
    void loginSucceedsAndMeUsesTokenPlant() throws Exception {
        Fixture fx = fixture();
        MvcResult login = login(fx.username, PASSWORD);
        JsonNode body = body(login);
        assertThat(body.get("accessToken").asString()).isNotBlank();
        assertThat(body.get("expiresIn").asLong()).isEqualTo(1800);
        assertThat(body.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(body.get("plant").get("id").asString()).isEqualTo(fx.plantId);
        assertThat(login.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");

        mvc.perform(bearer(get("/api/v1/auth/me"), body.get("accessToken").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(fx.username))
                .andExpect(jsonPath("$.user.superAdmin").value(true))
                .andExpect(jsonPath("$.person.code").exists())
                .andExpect(jsonPath("$.plant.id").value(fx.plantId))
                .andExpect(jsonPath("$.plants[0].isDefault").value(true))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.menus").isEmpty());

        mvc.perform(bearer(get("/api/v1/uoms"), body.get("accessToken").asString()))
                .andExpect(status().isOk());
    }

    @Test
    void loginFailureIsUniformAndLockoutHitsOnTheNextAttempt() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody-" + uniq("x") + "\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        Fixture fx = fixture();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(fx.username, "wrong-pass")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(fx.username, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        jdbc.update("UPDATE sys_user SET locked_until = now() - interval '1 minute' WHERE id = ?",
                UUID.fromString(fx.userId));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(fx.username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void refreshRotatesAndLogoutRevokesTheFamily() throws Exception {
        Fixture fx = fixture();
        MvcResult login = login(fx.username, PASSWORD);
        String first = refreshValue(login);

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshCookies.NAME, first)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie(RefreshCookies.NAME, first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String second = refreshValue(rotated);
        assertThat(second).isNotEqualTo(first);

        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie(RefreshCookies.NAME, first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie(RefreshCookies.NAME, second)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        MvcResult again = login(fx.username, PASSWORD);
        String live = refreshValue(again);
        String access = body(again).get("accessToken").asString();
        mvc.perform(post("/api/v1/auth/logout")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .cookie(new Cookie(RefreshCookies.NAME, live)))
                .andExpect(status().isNoContent());
        mvc.perform(bearer(get("/api/v1/auth/me"), access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie(RefreshCookies.NAME, live)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void switchToUnboundPlantIsRejected() throws Exception {
        Fixture fx = fixture();
        String other = insertPlant();
        String token = body(login(fx.username, PASSWORD)).get("accessToken").asString();
        mvc.perform(bearer(post("/api/v1/auth/switch-plant"), token)
                        .content("{\"plantId\":\"" + other + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLANT_FORBIDDEN"));

        JsonNode user = body(mvc.perform(bearer(get("/api/v1/users/" + fx.userId), token))
                .andExpect(status().isOk()).andReturn());
        int lock = user.get("lockVersion").asInt();
        mvc.perform(bearer(put("/api/v1/users/" + fx.userId + "/plants"), token)
                        .content("""
                                {"lockVersion":%d,"plants":[
                                  {"plantId":"%s","isDefault":true,"active":true},
                                  {"plantId":"%s","isDefault":false,"active":true}
                                ]}
                                """.formatted(lock, fx.plantId, other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plants.length()").value(2));

        MvcResult switched = mvc.perform(bearer(post("/api/v1/auth/switch-plant"), token)
                        .content("{\"plantId\":\"" + other + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plant.id").value(other))
                .andReturn();
        String next = body(switched).get("accessToken").asString();
        mvc.perform(bearer(get("/api/v1/auth/me"), next))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plant.id").value(other));
        mvc.perform(bearer(get("/api/v1/uoms"), next))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordEnforcesPolicyAndDropsSessions() throws Exception {
        Fixture admin = fixture();
        String adminToken = body(login(admin.username, PASSWORD)).get("accessToken").asString();
        String code = uniq("W");
        MvcResult created = mvc.perform(bearer(post("/api/v1/users"), adminToken)
                        .content(userJson(code, admin.plantId, true, "Init@Pass1")))
                .andExpect(status().isCreated())
                .andReturn();
        String username = body(created).get("username").asString();
        MvcResult first = login(username, "Init@Pass1");
        String token = body(first).get("accessToken").asString();
        String refresh = refreshValue(first);
        mvc.perform(bearer(get("/api/v1/auth/me"), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        change(token, "Init@Pass1", "Ab1!").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));
        change(token, "Init@Pass1", "abcdefgh").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("TOO_FEW_CLASSES"));
        change(token, "Init@Pass1", username + "Aa1!").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("CONTAINS_USERNAME"));
        change(token, "wrong-pass", "N3w-passw0rd").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        jdbc.update("UPDATE sys_config SET value = '12' WHERE config_key = 'security.password.min-length'");
        try {
            change(token, "Init@Pass1", "Abcd1234!").andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].code").value("TOO_SHORT"));
        } finally {
            jdbc.update("UPDATE sys_config SET value = '8' WHERE config_key = 'security.password.min-length'");
        }

        change(token, "Init@Pass1", "N3w-passw0rd").andExpect(status().isNoContent());
        mvc.perform(bearer(get("/api/v1/auth/me"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie(RefreshCookies.NAME, refresh)))
                .andExpect(status().isUnauthorized());

        String fresh = body(login(username, "N3w-passw0rd")).get("accessToken").asString();
        mvc.perform(bearer(get("/api/v1/auth/me"), fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
        mvc.perform(bearer(post("/api/v1/users"), fresh)
                        .content(userJson(uniq("Z"), admin.plantId, true, "Init@Pass1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void userCrudEnforcesOneDefaultAndOneAccount() throws Exception {
        Fixture admin = fixture();
        String token = body(login(admin.username, PASSWORD)).get("accessToken").asString();
        String other = insertPlant();
        mvc.perform(bearer(post("/api/v1/users"), token)
                        .content("""
                                {"code":"%s","name":"重复默认","password":"Init@Pass1","plants":[
                                  {"plantId":"%s","isDefault":true},
                                  {"plantId":"%s","isDefault":true}
                                ]}
                                """.formatted(uniq("D"), admin.plantId, other)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(bearer(post("/api/v1/users"), token)
                        .content("""
                                {"code":"%s","name":"没有工厂","password":"Init@Pass1","plants":[]}
                                """.formatted(uniq("D"))))
                .andExpect(status().isUnprocessableEntity());

        String code = uniq("D");
        MvcResult created = mvc.perform(bearer(post("/api/v1/users"), token)
                        .content(userJson(code, admin.plantId, true, "Init@Pass1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plants[0].isDefault").value(true))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();
        JsonNode user = body(created);
        String id = user.get("id").asString();
        String personId = user.get("person").get("id").asString();
        mvc.perform(bearer(get("/api/v1/users?q=" + code), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value(user.get("username").asString()));

        int lock = user.get("lockVersion").asInt();
        mvc.perform(bearer(put("/api/v1/users/" + id + "/plants"), token)
                        .content("""
                                {"lockVersion":%d,"plants":[
                                  {"plantId":"%s","isDefault":true},
                                  {"plantId":"%s","isDefault":true}
                                ]}
                                """.formatted(lock, admin.plantId, other)))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(bearer(post("/api/v1/users"), token)
                        .content(userJson(code, admin.plantId, true, "Init@Pass1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO sys_user (username, person_id, password_hash, status, must_change_pwd)
                        VALUES (?, ?, 'hash', 'ACTIVE', true)
                        """, "x" + uniq("u"), UUID.fromString(personId)));

        mvc.perform(bearer(delete("/api/v1/users/" + id + "?lockVersion=" + lock), token))
                .andExpect(status().isNoContent());
        mvc.perform(bearer(get("/api/v1/users/" + id), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void jwtModeRejectsHeaderOnlyExpiredAndBadTokens() throws Exception {
        Fixture fx = fixture();
        mvc.perform(get("/api/v1/uoms").header(PlantContextFilter.HEADER_PLANT_ID, fx.plantId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/uoms").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        String token = body(login(fx.username, PASSWORD)).get("accessToken").asString();
        String other = insertPlant();
        mvc.perform(get("/api/v1/uoms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(PlantContextFilter.HEADER_PLANT_ID, other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANT_MISMATCH"));
        mvc.perform(get("/api/v1/uoms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(PlantContextFilter.HEADER_PLANT_ID, fx.plantId))
                .andExpect(status().isOk());

        JwtService jwt = new JwtService(SECRET, Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));
        String expired = jwt.issue(UUID.fromString(fx.userId), UUID.fromString(fx.personId),
                UUID.fromString(fx.plantId), 0, Duration.ofMinutes(30));
        mvc.perform(get("/api/v1/uoms").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void seedAdminWithoutAssignmentCannotLogin() throws Exception {
        jdbc.update("UPDATE sys_user SET password_hash = ?, must_change_pwd = true, fail_count = 0, locked_until = NULL WHERE username = 'admin'",
                passwordHash);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("admin", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_PLANT_ASSIGNMENT"));
    }

    private org.springframework.test.web.servlet.ResultActions change(String token, String oldPassword, String newPassword)
            throws Exception {
        return mvc.perform(bearer(post("/api/v1/auth/change-password"), token)
                .content("{\"oldPassword\":\"" + oldPassword + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static String userJson(String code, String plantId, boolean isDefault, String password) {
        return """
                {"code":"%s","name":"用户%s","password":"%s","plants":[{"plantId":"%s","isDefault":%s}]}
                """.formatted(code, code, password, plantId, isDefault);
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private static String refreshValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(RefreshCookies.NAME + "=");
        return header.substring(RefreshCookies.NAME.length() + 1, header.indexOf(';'));
    }

    private Fixture fixture() {
        String plantId = insertPlant();
        UUID personId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO org_person (id, code, name) VALUES (?, ?, ?)",
                    personId, username.toUpperCase(), "超级管理员");
            jdbc.update("""
                    INSERT INTO sys_user
                        (id, username, person_id, password_hash, status, super_admin, must_change_pwd,
                         default_plant_id, perm_version)
                    VALUES (?, ?, ?, ?, 'ACTIVE', true, false, ?, 0)
                    """, userId, username, personId, passwordHash, UUID.fromString(plantId));
            jdbc.update("""
                    INSERT INTO org_person_plant (person_id, plant_id, active, is_default)
                    VALUES (?, ?, true, true)
                    """, personId, UUID.fromString(plantId));
        });
        return new Fixture(username, userId.toString(), personId.toString(), plantId);
    }

    private String insertPlant() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO org_plant (id, code, name) VALUES (?, ?, ?)", id, uniq("P"), "工厂");
        return id.toString();
    }

    private record Fixture(String username, String userId, String personId, String plantId) {
    }
}
