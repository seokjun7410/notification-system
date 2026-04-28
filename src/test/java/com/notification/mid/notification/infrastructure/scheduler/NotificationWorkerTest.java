package com.notification.mid.notification.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.notification.mid.notification.application.dto.NotificationDispatchTargetDto;
import com.notification.mid.notification.application.port.in.NotificationDispatchService;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.notification.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationWorkerTest {

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Test
    @DisplayName("worker는 dispatch를 한 건씩 선점하고 바로 처리한다")
    void claimsAndDispatchesOneByOne() {
        // given: worker가 순차 처리할 dispatch 대상 두 건이 있다
        NotificationWorker notificationWorker = new NotificationWorker(notificationDispatchService, 3);
        NotificationDispatchTargetDto firstTarget = createTarget("event-1");
        NotificationDispatchTargetDto secondTarget = createTarget("event-2");

        when(notificationDispatchService.claimDispatchTargets(1))
                .thenReturn(List.of(firstTarget))
                .thenReturn(List.of(secondTarget))
                .thenReturn(List.of());

        // when: worker를 한 번 실행하면
        int processedCount = notificationWorker.runOnce();

        // then: dispatch를 1건씩 선점하고 총 2건 처리한다
        assertThat(processedCount).isEqualTo(2);
        verify(notificationDispatchService, times(3)).claimDispatchTargets(1);
        verify(notificationDispatchService).dispatch(firstTarget);
        verify(notificationDispatchService).dispatch(secondTarget);
    }

    private NotificationDispatchTargetDto createTarget(String eventId) {
        return new NotificationDispatchTargetDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventId,
                "user-1",
                NotificationType.ORDER,
                NotificationChannel.EMAIL,
                "title",
                "content"
        );
    }
}
