package com.xihua.mes.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** The Flyway V2 must stay byte-identical to the reviewed draft docs/sql/V2__master_data.sql (except header). */
class MigrationDraftParityTest {

    private static String body(Path p) throws Exception {
        String s = Files.readString(p, StandardCharsets.UTF_8);
        int first = s.indexOf("-- =====");
        int second = s.indexOf("-- =====", first + 1);
        int eol = s.indexOf('\n', second);
        return s.substring(eol + 1);
    }

    @Test
    void migrationMatchesReviewedDraft() throws Exception {
        Path draft = Path.of("../../docs/sql/V2__master_data.sql");
        Path migration = Path.of("src/main/resources/db/migration/V2__master_data.sql");
        assertTrue(Files.exists(draft), "draft not found at " + draft.toAbsolutePath());
        assertEquals(body(draft), body(migration));
    }
}
