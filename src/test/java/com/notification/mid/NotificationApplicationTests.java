package com.notification.mid;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationApplicationTests {

    @Test
    @org.junit.jupiter.api.DisplayName("스프링 컨텍스트가 정상적으로 기동된다")
    void contextLoads() {
        // given: 테스트용 애플리케이션 설정이 준비되어 있다

        // when: 스프링 컨텍스트를 로드하면

        // then: 예외 없이 애플리케이션이 기동된다
    }

}
