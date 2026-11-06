/**
 * Auth and plant-scoped access.
 * Business code: {@link com.xihua.mes.auth.CurrentContext}.
 * Storage: {@link com.xihua.mes.auth.PlantContext} + header filter when {@code mes.auth.mode=header}.
 * After JWT: populate CurrentContext from the token — do not trust client {@code X-Plant-Id}.
 */
package com.xihua.mes.auth;
