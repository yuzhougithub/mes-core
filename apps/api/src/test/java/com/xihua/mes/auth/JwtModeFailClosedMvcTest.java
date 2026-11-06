package com.xihua.mes.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.xihua.mes.common.ApiExceptionHandler;

/**
 * Default {@code jwt} mode is not implemented: a forged {@code X-Plant-Id} must not become the plant.
 */
@WebMvcTest(controllers = PlantRequireTestController.class)
@AutoConfigureMockMvc
@Import({PlantContextFilter.class, ApiExceptionHandler.class})
@TestPropertySource(properties = "mes.auth.mode=jwt")
class JwtModeFailClosedMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void forgedPlantHeaderDoesNotSatisfyPlantContext() throws Exception {
        mockMvc.perform(get("/__test/plant").header(PlantContextFilter.HEADER_PLANT_ID, "forged-plant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANT_REQUIRED"));
    }
}
