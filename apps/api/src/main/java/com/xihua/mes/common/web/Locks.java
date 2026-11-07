package com.xihua.mes.common.web;

import java.util.List;
import java.util.Objects;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.FieldError;

/** Optimistic-lock helpers (API field {@code lockVersion}; 409 {@code STALE_VERSION}). */
public final class Locks {

    private Locks() {
    }

    /** @throws ApiException 422 when missing, 409 when different from {@code current} */
    public static void check(Integer requested, Integer current) {
        if (requested == null) {
            throw ApiException.validation(List.of(
                    new FieldError("lockVersion", ErrorCodes.REQUIRED, "lockVersion 必填")));
        }
        if (!Objects.equals(requested, current)) {
            throw ApiException.staleVersion();
        }
    }

    /** @throws ApiException 409 when an optimistic update touched no row */
    public static void requireUpdated(int rows) {
        if (rows != 1) {
            throw ApiException.staleVersion();
        }
    }
}
