package com.xihua.mes.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
    void missingPlantHeaderReturns400PlantRequired() throws Exception {
        mockMvc.perform(get("/__test/plant"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PLANT_REQUIRED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.traceId").isString());
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
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PLANT_HEADER_INVALID"))
                .andExpect(content().string(containsString("PLANT_HEADER_INVALID")));
    }
}
