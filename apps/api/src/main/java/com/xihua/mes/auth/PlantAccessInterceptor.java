package com.xihua.mes.auth;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

import com.xihua.mes.common.error.ApiException;

/**
 * Business endpoints under {@code /api/v1/**} (except global {@code /plants}) require a current plant:
 * missing → 400 {@code PLANT_REQUIRED}; not a UUID / unknown / disabled → 403 {@code PLANT_FORBIDDEN}.
 * Header mode treats the caller as {@code ADMIN} but still checks the plant exists (overview «认证»).
 */
public class PlantAccessInterceptor implements HandlerInterceptor {

    private final PlantDirectory plants;

    public PlantAccessInterceptor(PlantDirectory plants) {
        this.plants = plants;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID plantId = CurrentContext.requirePlantUuid();
        if (NoPlant.ID.equals(plantId)) {
            if (!NoPlant.allows(request)) {
                throw ApiException.noPlantAssignment();
            }
            return true;
        }
        if (!plants.isActive(plantId)) {
            throw ApiException.plantForbidden("工厂不存在或已停用：" + plantId);
        }
        return true;
    }
}
