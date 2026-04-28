package com.notification.mid.notification.presentation.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notification.mid.common.exception.shared.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = TestExceptionWebController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("검증 예외가 발생하면 INVALID_REQUEST 응답을 반환한다")
    void returnsInvalidRequestWhenValidationFails() throws Exception {
        // given: 필수값이 비어 있는 요청이 있다
        String requestBody = objectMapper.writeValueAsString(new TestExceptionWebController.TestReq(""));

        // when: 검증 대상 API를 호출하면
        var response = mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // then: INVALID_REQUEST 응답으로 변환된다
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("name은 필수입니다."));
    }

    @Test
    @DisplayName("비즈니스 예외가 발생하면 ErrorCode에 맞는 응답을 반환한다")
    void returnsBusinessErrorResponse() throws Exception {
        // given: 비즈니스 예외를 던지는 API가 있다

        // when: 해당 API를 호출하면
        var response = mockMvc.perform(get("/test/business"));

        // then: ErrorCode에 맞는 응답으로 변환된다
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("알림을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("처리되지 않은 예외가 발생하면 INTERNAL_ERROR 응답을 반환한다")
    void returnsInternalErrorResponse() throws Exception {
        // given: 처리되지 않은 예외를 던지는 API가 있다

        // when: 해당 API를 호출하면
        var response = mockMvc.perform(get("/test/internal"));

        // then: INTERNAL_ERROR 응답으로 변환된다
        response.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }
}
