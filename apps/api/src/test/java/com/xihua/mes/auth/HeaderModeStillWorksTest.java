package com.xihua.mes.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.xihua.mes.support.PgIntegrationTest;

/** Dev/test header mode keeps trusting X-Plant-Id and does not require a bearer token. */
class HeaderModeStillWorksTest extends PgIntegrationTest {

    @Test
    void headerModeMeAndBusinessApiDoNotRequireJwt() throws Exception {
        String plantId = UUID.randomUUID().toString();
        String userId = "header-user";
        mvc.perform(get("/api/v1/auth/me")
                        .header(PlantContextFilter.HEADER_PLANT_ID, plantId)
                        .header(PlantContextFilter.HEADER_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(jsonPath("$.plant.id").value(plantId))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.menus").isEmpty());

        String realPlant = createPlant();
        mvc.perform(get("/api/v1/uoms").header(PlantContextFilter.HEADER_PLANT_ID, realPlant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }
}
