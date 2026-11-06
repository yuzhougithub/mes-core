package com.xihua.mes.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Full Boot + Flyway V1/V2 on embedded PostgreSQL 17, MockMvc, header auth mode. */
@SpringBootTest(properties = "mes.auth.mode=header")
@AutoConfigureMockMvc
public abstract class PgIntegrationTest {

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    protected MockMvc mvc;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", EmbeddedPg::jdbcUrl);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    protected static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected static MockHttpServletRequestBuilder withPlant(MockHttpServletRequestBuilder b, String plantId) {
        return b.header("X-Plant-Id", plantId).contentType(MediaType.APPLICATION_JSON);
    }

    protected String createPlant() throws Exception {
        String code = uniq("P");
        MvcResult r = mvc.perform(post("/api/v1/plants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"工厂" + code + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return body(r).get("id").stringValue();
    }

    protected String createUom(String plantId) throws Exception {
        String code = uniq("U").substring(0, 10);
        MvcResult r = mvc.perform(withPlant(post("/api/v1/uoms"), plantId)
                        .content("{\"code\":\"" + code + "\",\"name\":\"件\",\"decimals\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        return body(r).path("id").stringValue();
    }
}
