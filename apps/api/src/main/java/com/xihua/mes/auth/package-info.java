/**
 * Auth and plant-scoped access.
 * Skeleton: {@link com.xihua.mes.auth.CurrentContext} / {@link com.xihua.mes.auth.PlantContext}
 * + {@code X-Plant-Id} filter when {@code mes.auth.mode=header}; no JWT yet.
 * After JWT: populate CurrentContext from the token — do not trust client {@code X-Plant-Id}.
 */
package com.xihua.mes.auth;
