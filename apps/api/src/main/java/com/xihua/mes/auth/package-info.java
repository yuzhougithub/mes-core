/**
 * Auth and plant-scoped access.
 * Business code: {@link com.xihua.mes.auth.CurrentContext}.
 * Storage: {@link com.xihua.mes.auth.PlantContext} + header filter when {@code mes.auth.mode=header}
 * (dev/test only). Default {@code jwt} ignores client {@code X-Plant-Id} and fails closed
 * until a token adapter writes {@link com.xihua.mes.auth.CurrentContext}.
 */
package com.xihua.mes.auth;
