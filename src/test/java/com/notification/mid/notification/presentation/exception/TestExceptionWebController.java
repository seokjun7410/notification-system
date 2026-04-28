package com.notification.mid.notification.presentation.exception;

import com.notification.mid.common.exception.shared.BusinessException;
import com.notification.mid.common.exception.shared.ErrorCode;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@Validated
@RequestMapping("/test")
class TestExceptionWebController {

    @PostMapping("/validation")
    String validation(@Valid @RequestBody TestReq req) {
        return "ok";
    }

    @GetMapping("/business")
    String business() {
        throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @GetMapping("/internal")
    String internal() {
        throw new IllegalStateException("unexpected");
    }

    record TestReq(
            @NotBlank(message = "name은 필수입니다.")
            String name
    ) {
    }
}
