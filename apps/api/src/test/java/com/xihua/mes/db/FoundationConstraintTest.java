package com.xihua.mes.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.xihua.mes.support.PgIntegrationTest;

/** Database rules: one account per person, and exactly one default plant when any assignment exists. */
class FoundationConstraintTest extends PgIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void personWithoutAccountIsRejected() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> tx().executeWithoutResult(status -> jdbc.update(
                "INSERT INTO org_person (id, code, name) VALUES (?, ?, ?)",
                UUID.randomUUID(), uniq("NP"), "无名")));
        assertThat(text(ex)).contains("exactly one account");
    }

    @Test
    void secondAccountForSamePersonIsRejected() {
        UUID personId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        tx().executeWithoutResult(status -> {
            jdbc.update("INSERT INTO org_plant (id, code, name) VALUES (?, ?, ?)", plantId, uniq("P"), "厂");
            jdbc.update("INSERT INTO org_person (id, code, name) VALUES (?, ?, ?)", personId, uniq("C"), "甲");
            insertUser(personId, "a" + uniq("u"));
            jdbc.update("""
                    INSERT INTO org_person_plant (person_id, plant_id, active, is_default)
                    VALUES (?, ?, true, true)
                    """, personId, plantId);
        });
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO sys_user (username, person_id, password_hash, status, must_change_pwd)
                        VALUES (?, ?, 'hash', 'ACTIVE', true)
                        """, "b" + uniq("u"), personId));
    }

    @Test
    void assignmentRequiresExactlyOneDefault() {
        UUID personId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        tx().executeWithoutResult(status -> {
            jdbc.update("INSERT INTO org_plant (id, code, name) VALUES (?, ?, ?)", plantId, uniq("P"), "厂");
            jdbc.update("INSERT INTO org_person (id, code, name) VALUES (?, ?, ?)", personId, uniq("C"), "乙");
            insertUser(personId, "c" + uniq("u"));
            jdbc.update("""
                    INSERT INTO org_person_plant (person_id, plant_id, active, is_default)
                    VALUES (?, ?, true, true)
                    """, personId, plantId);
        });

        UUID otherPlant = UUID.randomUUID();
        jdbc.update("INSERT INTO org_plant (id, code, name) VALUES (?, ?, ?)", otherPlant, uniq("P"), "厂二");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO org_person_plant (person_id, plant_id, active, is_default)
                VALUES (?, ?, true, true)
                """, personId, otherPlant));

        RuntimeException missing = assertThrows(RuntimeException.class, () -> tx().executeWithoutResult(status -> {
            UUID person = UUID.randomUUID();
            UUID plant = UUID.randomUUID();
            jdbc.update("INSERT INTO org_plant (id, code, name) VALUES (?, ?, ?)", plant, uniq("P"), "厂三");
            jdbc.update("INSERT INTO org_person (id, code, name) VALUES (?, ?, ?)", person, uniq("C"), "丙");
            insertUser(person, "d" + uniq("u"));
            jdbc.update("""
                    INSERT INTO org_person_plant (person_id, plant_id, active, is_default)
                    VALUES (?, ?, true, false)
                    """, person, plant);
        }));
        assertThat(text(missing)).contains("exactly one default");
    }

    private void insertUser(UUID personId, String username) {
        jdbc.update("""
                INSERT INTO sys_user (username, person_id, password_hash, status, must_change_pwd)
                VALUES (?, ?, 'hash', 'ACTIVE', true)
                """, username.toLowerCase(), personId);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactions);
    }

    private static String text(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return text.toString();
    }
}
