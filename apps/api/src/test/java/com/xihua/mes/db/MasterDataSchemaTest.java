package com.xihua.mes.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xihua.mes.support.PgIntegrationTest;

/** Flyway V2 applied on real PostgreSQL 17: table count, seeds, PG-only constraints. */
class MasterDataSchemaTest extends PgIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v2TablesRemainAndV3FoundationIsApplied() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name ~ '^(org|mat|rtg|res|cfg)_'""", Integer.class);
        assertEquals(32, tables);
        assertThat(jdbc.queryForList("SELECT code FROM org_role ORDER BY code", String.class))
                .containsExactly("ADMIN", "EDITOR", "VIEWER");
        assertThat(jdbc.queryForObject("SELECT max(version) FROM flyway_schema_history", String.class))
                .isEqualTo("7");
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.sys_user') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.sys_menu') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.sys_role_menu') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.auth_refresh_token') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.org_dept') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.org_post') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.org_person_post') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.sys_role_data_dept') IS NOT NULL", Boolean.class)).isTrue();
        Integer server = jdbc.queryForObject("SELECT current_setting('server_version_num')::int", Integer.class);
        assertThat(server).isGreaterThanOrEqualTo(150000);
    }

    @Test
    void sharedRoutingVersionUniqueUsesNullsNotDistinct() {
        String code = "RT-" + UUID.randomUUID();
        jdbc.update("INSERT INTO rtg_routing (code, version_no, name) VALUES (?, 'A', 'n')", code);
        assertThrows(DuplicateKeyException.class,
                () -> jdbc.update("INSERT INTO rtg_routing (code, version_no, name) VALUES (?, 'A', 'n')", code));
    }

    @Test
    void atMostOneReleasedPerScopeAndSoftDeletedCodesAreReusable() {
        String code = "RT-" + UUID.randomUUID();
        jdbc.update("INSERT INTO rtg_routing (code, version_no, name, status) VALUES (?, 'A', 'n', 'RELEASED')",
                code);
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO rtg_routing (code, version_no, name, status) VALUES (?, 'B', 'n', 'RELEASED')", code));
        jdbc.update("UPDATE rtg_routing SET deleted = true WHERE code = ?", code);
        jdbc.update("INSERT INTO rtg_routing (code, version_no, name, status) VALUES (?, 'A', 'n', 'RELEASED')",
                code);
    }
}
