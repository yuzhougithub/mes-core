package com.xihua.mes.common.persistence;

import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;

/**
 * Second-layer data scope. Sits in front of {@link PlantLineInnerInterceptor}; both apply.
 * Built on MyBatis-Plus {@link DataPermissionInterceptor} and {@code MultiDataPermissionHandler}.
 * Only statements reached from {@link DataScope} are rewritten.
 */
public class DataScopeInnerInterceptor extends DataPermissionInterceptor {

    public DataScopeInnerInterceptor() {
        super(new DataScopeHandler());
    }
}
