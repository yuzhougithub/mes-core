package com.xihua.mes.support;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * One real PostgreSQL 17 per test JVM (zonky embedded binaries; no Docker needed).
 * Covers PG-only features of V2 (NULLS NOT DISTINCT, partial unique indexes, jsonb).
 */
public final class EmbeddedPg {

    private static EmbeddedPostgres instance;

    private EmbeddedPg() {
    }

    public static synchronized String jdbcUrl() {
        if (instance == null) {
            try {
                instance = EmbeddedPostgres.builder().start();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            EmbeddedPostgres pg = instance;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }));
        }
        return instance.getJdbcUrl("postgres", "postgres");
    }
}
