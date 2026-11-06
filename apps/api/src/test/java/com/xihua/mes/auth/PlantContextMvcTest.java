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
 * Slice test: Filter + Advice + thin controller — no Flyway / full Boot.
 */
@WebMvcTest(controllers = PlantRequireTestController.class)
@AutoConfigureMockMvc
@Import({PlantContextFilter.class, ApiExceptionHandler.class})
@TestPropertySource(properties = "mes.auth.mode=header")
class PlantContextMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingPlantHeaderReturns400PlantNotSet() throws Exception {
        mockMvc.perform(get("/__test/plant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANT_NOT_SET"));
    }

    @Test
    void withPlantHeaderReturnsPlantId() throws Exception {
        mockMvc.perform(get("/__test/plant").header(PlantContextFilter.HEADER_PLANT_ID, "plant-mvc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantId").value("plant-mvc"));
    }

    @Test
    void blankPlantHeaderReturns400PlantHeaderInvalid() throws Exception {
        mockMvc.perform(get("/__test/plant").header(PlantContextFilter.HEADER_PLANT_ID, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANT_HEADER_INVALID"));
    }
}
