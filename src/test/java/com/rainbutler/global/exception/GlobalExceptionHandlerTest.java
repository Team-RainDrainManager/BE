package com.rainbutler.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GlobalExceptionHandler가 예외를 공통 실패 응답으로 바꾸는지 검증합니다.
 */
@WebMvcTest(controllers = ExceptionTestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 비즈니스_예외는_ErrorCode의_상태와_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/exceptions/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("DRAIN_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message")
                        .value(ErrorCode.DRAIN_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.error.fieldErrors").isEmpty());
    }

    @Test
    void 요청_본문_검증_실패는_400과_fieldErrors로_응답한다() throws Exception {
        mockMvc.perform(post("/test/exceptions/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"))
                .andExpect(jsonPath("$.error.fieldErrors[0].reason").isNotEmpty());
    }

    @Test
    void 잘못된_JSON은_400_INVALID_INPUT으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/exceptions/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 알_수_없는_예외는_500으로_응답하고_원인을_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/exceptions/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("내부 비밀 정보"))));
    }
}
