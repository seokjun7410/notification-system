package com.notification.mid.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import java.time.LocalDateTime;
import java.util.List;

import com.notification.mid.notification.application.exception.NotificationSendTimeoutException;
import com.notification.mid.notification.application.exception.NonRetryableNotificationSendException;
import com.notification.mid.notification.application.exception.RetryableNotificationSendException;
import com.notification.mid.notification.application.dto.NotificationDispatchTargetDto;
import com.notification.mid.notification.application.port.in.NotificationDispatchService;
import com.notification.mid.notification.application.port.out.NotificationDispatchCommandPort;
import com.notification.mid.notification.application.port.out.NotificationSender;
import com.notification.mid.notification.domain.attempt.NotificationAttempt;
import com.notification.mid.notification.domain.attempt.NotificationAttemptResultStatus;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;
import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationAttemptRepository;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationDispatchRepository;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationRepository;
import com.notification.mid.notification.support.fixture.NotificationDispatchFixture;
import com.notification.mid.notification.support.fixture.NotificationFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class NotificationDispatchServiceIntegrationTest {

    private static final String TEST_FAILURE_MESSAGE = "dispatch failed in test";
    private static final String TEST_TIMEOUT_MESSAGE = "timeout in test";

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private JpaNotificationRepository notificationRepository;

    @Autowired
    private JpaNotificationDispatchRepository notificationDispatchRepository;

    @Autowired
    private JpaNotificationAttemptRepository notificationAttemptRepository;

    @Autowired
    private NotificationDispatchCommandPort notificationDispatchCommandPort;

    @Autowired
    private NotificationSender notificationSender;

    @BeforeEach
    void setUp() {
        notificationAttemptRepository.deleteAll();
        notificationDispatchRepository.deleteAll();
        notificationRepository.deleteAll();
        reset(notificationSender);
        doNothing().when(notificationSender).send(any(Notification.class), any(NotificationDispatch.class));
    }

    @Test
    @DisplayName("worker가 처리 가능한 dispatch를 가져가면 SUCCESS로 처리할 수 있다")
    void dispatchesNotificationSuccessfully() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 26, 10, 0);

        // given: 아직 처리되지 않은 EMAIL 발송 정보가 저장되어 있다
        Notification notification = notificationRepository.save(NotificationFixture.createNotification(createdAt));
        NotificationDispatch notificationDispatch = notificationDispatchRepository.save(
                NotificationDispatchFixture.createPendingDispatch(notification, NotificationChannel.EMAIL, createdAt)
        );

        // when: worker가 처리 대상을 점유하고 발송을 수행하면
        List<NotificationDispatchTargetDto> claimedTargets = notificationDispatchService.claimDispatchTargets(10);
        notificationDispatchService.dispatch(claimedTargets.getFirst());

        NotificationDispatch foundDispatch = notificationDispatchRepository.findById(notificationDispatch.getId()).orElseThrow();
        List<NotificationAttempt> attempts = notificationAttemptRepository
                .findByNotificationDispatchIdOrderByAttemptNoAscIdAsc(notificationDispatch.getId());

        // then: 발송 정보는 SUCCESS 상태가 되고 시도 이력이 1건 저장된다
        assertThat(claimedTargets).hasSize(1);
        assertThat(claimedTargets.getFirst().dispatchId()).isEqualTo(notificationDispatch.getId());
        assertThat(foundDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.SUCCESS);
        assertThat(foundDispatch.getNextRetryAt()).isNull();
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().getResultStatus()).isEqualTo(NotificationAttemptResultStatus.SUCCESS);
    }

    @Test
    @DisplayName("발송 실패 시 RETRY_WAIT로 전환하고 retryCount를 증가시킨다")
    void movesToRetryWaitWhenDispatchFails() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 26, 10, 0);

        // given: sender가 재시도 가능한 실패 예외를 던지도록 설정된 EMAIL 발송 정보가 있다
        doThrow(new RetryableNotificationSendException(TEST_FAILURE_MESSAGE))
                .when(notificationSender)
                .send(any(Notification.class), any(NotificationDispatch.class));

        Notification notification = notificationRepository.save(NotificationFixture.createNotification(createdAt));
        NotificationDispatch notificationDispatch = notificationDispatchRepository.save(
                NotificationDispatchFixture.createPendingDispatch(notification, NotificationChannel.EMAIL, createdAt)
        );

        // when: worker가 발송을 수행하면
        List<NotificationDispatchTargetDto> claimedTargets = notificationDispatchService.claimDispatchTargets(10);
        notificationDispatchService.dispatch(claimedTargets.getFirst());

        NotificationDispatch foundDispatch = notificationDispatchRepository.findById(notificationDispatch.getId()).orElseThrow();
        List<NotificationAttempt> attempts = notificationAttemptRepository
                .findByNotificationDispatchIdOrderByAttemptNoAscIdAsc(notificationDispatch.getId());

        // then: 발송 정보는 재시도 대기 상태가 되고 실패 이력이 남는다
        assertThat(foundDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.RETRY_WAIT);
        assertThat(foundDispatch.getRetryCount()).isEqualTo(1);
        assertThat(foundDispatch.getNextRetryAt()).isNotNull();
        assertThat(foundDispatch.getLastError()).isEqualTo(TEST_FAILURE_MESSAGE);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().getResultStatus()).isEqualTo(NotificationAttemptResultStatus.FAILURE);
    }

    @Test
    @DisplayName("재시도 불가능한 실패면 즉시 FAILED로 종료한다")
    void marksFailedImmediatelyWhenDispatchFailsPermanently() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 26, 10, 0);

        // given: sender가 재시도 불가능한 실패 예외를 던지도록 설정된 EMAIL 발송 정보가 있다
        doThrow(new NonRetryableNotificationSendException(TEST_FAILURE_MESSAGE))
                .when(notificationSender)
                .send(any(Notification.class), any(NotificationDispatch.class));

        Notification notification = notificationRepository.save(NotificationFixture.createNotification(createdAt));
        NotificationDispatch notificationDispatch = notificationDispatchRepository.save(
                NotificationDispatchFixture.createPendingDispatch(notification, NotificationChannel.EMAIL, createdAt)
        );

        // when: worker가 발송을 수행하면
        List<NotificationDispatchTargetDto> claimedTargets = notificationDispatchService.claimDispatchTargets(10);
        notificationDispatchService.dispatch(claimedTargets.getFirst());

        NotificationDispatch foundDispatch = notificationDispatchRepository.findById(notificationDispatch.getId()).orElseThrow();
        List<NotificationAttempt> attempts = notificationAttemptRepository
                .findByNotificationDispatchIdOrderByAttemptNoAscIdAsc(notificationDispatch.getId());

        // then: 재시도 없이 즉시 FAILED로 종료된다
        assertThat(foundDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.FAILED);
        assertThat(foundDispatch.getRetryCount()).isEqualTo(1);
        assertThat(foundDispatch.getNextRetryAt()).isNull();
        assertThat(foundDispatch.getLastError()).isEqualTo(TEST_FAILURE_MESSAGE);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().getResultStatus()).isEqualTo(NotificationAttemptResultStatus.FAILURE);
    }

    @Test
    @DisplayName("이메일 timeout이 발생하면 UNKNOWN으로 전환하고 재시도하지 않는다")
    void marksUnknownWhenDispatchTimesOut() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 26, 10, 0);

        // given: sender가 timeout 예외를 던지도록 설정된 EMAIL 발송 정보가 있다
        doThrow(new NotificationSendTimeoutException(TEST_TIMEOUT_MESSAGE))
                .when(notificationSender)
                .send(any(Notification.class), any(NotificationDispatch.class));

        Notification notification = notificationRepository.save(NotificationFixture.createNotification(createdAt));
        NotificationDispatch notificationDispatch = notificationDispatchRepository.save(
                NotificationDispatchFixture.createPendingDispatch(notification, NotificationChannel.EMAIL, createdAt)
        );

        // when: worker가 발송을 수행하면
        List<NotificationDispatchTargetDto> claimedTargets = notificationDispatchService.claimDispatchTargets(10);
        notificationDispatchService.dispatch(claimedTargets.getFirst());

        NotificationDispatch foundDispatch = notificationDispatchRepository.findById(notificationDispatch.getId()).orElseThrow();
        List<NotificationAttempt> attempts = notificationAttemptRepository
                .findByNotificationDispatchIdOrderByAttemptNoAscIdAsc(notificationDispatch.getId());

        // then: 발송 정보는 UNKNOWN이 되고 retryCount는 증가하지 않는다
        assertThat(foundDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.UNKNOWN);
        assertThat(foundDispatch.getRetryCount()).isZero();
        assertThat(foundDispatch.getNextRetryAt()).isNull();
        assertThat(foundDispatch.getLastError()).isEqualTo(TEST_TIMEOUT_MESSAGE);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().getResultStatus()).isEqualTo(NotificationAttemptResultStatus.TIMEOUT);
    }

    @Test
    @DisplayName("최대 재시도 초과 후 실패하면 FAILED로 종료한다")
    void marksFailedAfterMaxRetry() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 26, 9, 0);
        LocalDateTime processingAt = LocalDateTime.of(2026, 4, 26, 9, 1);
        LocalDateTime nextRetryAt = LocalDateTime.of(2026, 4, 26, 9, 2);

        // given: sender가 재시도 가능한 실패 예외를 던지고 이미 3회 재시도한 EMAIL 발송 정보가 있다
        doThrow(new RetryableNotificationSendException(TEST_FAILURE_MESSAGE))
                .when(notificationSender)
                .send(any(Notification.class), any(NotificationDispatch.class));

        Notification notification = notificationRepository.save(NotificationFixture.createNotification(createdAt));
        NotificationDispatch notificationDispatch = NotificationDispatchFixture.createRetryWaitDispatch(
                notification,
                NotificationChannel.EMAIL,
                createdAt,
                processingAt,
                3,
                nextRetryAt,
                "previous failure"
        );
        NotificationDispatch savedDispatch = notificationDispatchRepository.save(notificationDispatch);

        // when: worker가 다시 발송을 시도하면
        List<NotificationDispatchTargetDto> claimedTargets = notificationDispatchService.claimDispatchTargets(10);
        notificationDispatchService.dispatch(claimedTargets.getFirst());

        NotificationDispatch foundDispatch = notificationDispatchRepository.findById(savedDispatch.getId()).orElseThrow();
        List<NotificationAttempt> attempts = notificationAttemptRepository
                .findByNotificationDispatchIdOrderByAttemptNoAscIdAsc(savedDispatch.getId());

        // then: 재시도 한도를 초과해 FAILED로 종료된다
        assertThat(foundDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.FAILED);
        assertThat(foundDispatch.getRetryCount()).isEqualTo(4);
        assertThat(foundDispatch.getNextRetryAt()).isNull();
        assertThat(foundDispatch.getLastError()).isEqualTo(TEST_FAILURE_MESSAGE);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().getAttemptNo()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("최종 상태 update는 PROCESSING 상태일 때만 반영된다")
    void finalizesOnlyWhenProcessing() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 26, 9, 0);
        LocalDateTime processingAt = LocalDateTime.of(2026, 4, 26, 9, 1);
        LocalDateTime recoveredAt = LocalDateTime.of(2026, 4, 26, 9, 7);

        // given: recovery가 먼저 UNKNOWN으로 바꾼 PROCESSING dispatch가 있다
        Notification notification = notificationRepository.saveAndFlush(
                NotificationFixture.builder(createdAt)
                        .eventId("event-finalize-only-1")
                        .build()
        );
        NotificationDispatch savedDispatch = notificationDispatchRepository.saveAndFlush(
                NotificationDispatchFixture.createProcessingDispatch(
                        notification,
                        NotificationChannel.EMAIL,
                        createdAt,
                        processingAt
                )
        );

        // when: recovery 후에 worker가 SUCCESS 반영을 시도하면
        boolean recovered = notificationDispatchCommandPort.applyStateChange(
                savedDispatch.getId(),
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.UNKNOWN,
                savedDispatch.getRetryCount(),
                null,
                "recovered",
                null,
                recoveredAt
        );
        boolean updated = notificationDispatchCommandPort.applyStateChange(
                savedDispatch.getId(),
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.SUCCESS,
                savedDispatch.getRetryCount(),
                null,
                null,
                null,
                recoveredAt.plusMinutes(1)
        );

        NotificationDispatch foundDispatch = notificationDispatchRepository.findById(savedDispatch.getId()).orElseThrow();

        // then: recovery는 반영되고 뒤늦은 SUCCESS 반영은 무시된다
        assertThat(recovered).isTrue();
        assertThat(updated).isFalse();
        assertThat(foundDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.UNKNOWN);
        assertThat(foundDispatch.getLastError()).isEqualTo("recovered");
    }

    @TestConfiguration
    static class NotificationSenderTestConfig {

        @Bean
        @Primary
        NotificationSender notificationSender() {
            return mock(NotificationSender.class);
        }
    }
}
