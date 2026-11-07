package com.xihua.mes.common.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.xihua.mes.auth.CurrentContext;

/**
 * Fills {@code created_at / created_by / updated_at / updated_by}.
 * {@code *_by} = {@link CurrentContext#userUuid()} (null until auth lands or if X-User-Id is not a UUID).
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID user = CurrentContext.userUuid();
        setIfPresent(metaObject, "createdAt", now);
        setIfPresent(metaObject, "updatedAt", now);
        setIfPresent(metaObject, "createdBy", user);
        setIfPresent(metaObject, "updatedBy", user);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        setIfPresent(metaObject, "updatedAt", OffsetDateTime.now());
        setIfPresent(metaObject, "updatedBy", CurrentContext.userUuid());
    }

    private void setIfPresent(MetaObject metaObject, String field, Object value) {
        if (metaObject.hasSetter(field)) {
            setFieldValByName(field, value, metaObject);
        }
    }
}
