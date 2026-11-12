package com.rainbutler.global.docs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Swagger(OpenAPI) 스키마가 실제 응답 DTO와 맞는지 검증합니다.
 *
 * <p>클래스 이름이 같은 DTO가 있으면 springdoc 컴포넌트가 덮어써져 문서만 잘못 나갈 수 있습니다.
 */
@AutoConfigureMockMvc
class OpenApiSchemaTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 빗물받이_목록_스키마에_주소와_좌표가_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.DrainResponse.properties.id").exists())
                .andExpect(jsonPath("$.components.schemas.DrainResponse.properties.address").exists())
                .andExpect(jsonPath("$.components.schemas.DrainResponse.properties.latitude").exists())
                .andExpect(jsonPath("$.components.schemas.DrainResponse.properties.longitude").exists())
                .andExpect(jsonPath("$.components.schemas.PageResponseDrainResponse"
                        + ".properties.content.items['$ref']").value("#/components/schemas/DrainResponse"));
    }

    @Test
    void 점검_상세_빗물받이_스키마는_따로_둔다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.InspectionDrainResponse"
                        + ".properties.drainId").exists())
                .andExpect(jsonPath("$.components.schemas.InspectionDrainResponse"
                        + ".properties.name").exists())
                .andExpect(jsonPath("$.components.schemas.InspectionDetailResponse"
                        + ".properties.drain['$ref']")
                        .value("#/components/schemas/InspectionDrainResponse"));
    }
}
