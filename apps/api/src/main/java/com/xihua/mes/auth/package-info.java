/**
 * Auth and plant-scoped access.
 * Business code: {@link com.xihua.mes.auth.CurrentContext}.
 * Storage: {@link com.xihua.mes.auth.PlantContext} + header filter when {@code mes.auth.mode=header}
 * (dev/test only). Default {@code jwt} fills {@link com.xihua.mes.auth.CurrentContext} from the
 * access token and rejects a mismatched or header-only {@code X-Plant-Id}.
 */
package com.xihua.mes.auth;
