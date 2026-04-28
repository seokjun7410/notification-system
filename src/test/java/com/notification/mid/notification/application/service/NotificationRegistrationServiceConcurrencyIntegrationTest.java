package com.notification.mid.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.notification.mid.common.exception.DuplicateNotificationRequestException;
import com.notification.mid.common.exception.IdempotencyPayloadMismatchException;
import com.notification.mid.notification.application.dto.response.NotificationCreateRes;
import com.notification.mid.notification.application.port.in.NotificationRegistrationService;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.notification.NotificationType;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationAttemptRepository;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationDispatchRepository;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationRegistrationServiceConcurrencyIntegrationTest {

    @Autowired
    private NotificationRegistrationService notificationRegistrationService;

    @Autowired
    private JpaNotificationRepository notificationRepository;

    @Autowired
    private JpaNotificationDispatchRepository notificationDispatchRepository;

    @Autowired
    private JpaNotificationAttemptRepository notificationAttemptRepository;

    @BeforeEach
    void setUp() {
        notificationAttemptRepository.deleteAll();
        notificationDispatchRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("동시에 같은 요청이 들어와도 notification 1건과 dispatch 1건만 생성된다")
    void createsSingleNotificationAndDispatchForConcurrentDuplicateRequests() throws Exception {
        int requestCount = 8;

        // given: 동일한 등록 요청이 동시에 여러 번 들어온다
        List<RegistrationOutcome> outcomes = runConcurrently(requestCount, () -> {
            try {
                notificationRegistrationService.register(
                        "concurrent-event-1",
                        "user-1",
                        NotificationType.ORDER,
                        NotificationChannel.EMAIL,
                        "주문 결제가 완료되었습니다",
                        "주문번호 10001의 결제가 완료되었습니다."
                );
                return RegistrationOutcome.SUCCESS;
            } catch (DuplicateNotificationRequestException exception) {
                return RegistrationOutcome.DUPLICATE;
            }
        });

        // then: 성공은 1건만 남고 나머지는 duplicate로 정리된다
        assertThat(countOutcome(outcomes, RegistrationOutcome.SUCCESS)).isEqualTo(1);
        assertThat(countOutcome(outcomes, RegistrationOutcome.DUPLICATE)).isEqualTo(requestCount - 1);
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(notificationDispatchRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 멱등성 키에 다른 payload가 동시에 들어오면 한 건만 생성되고 나머지는 mismatch로 거절된다")
    void rejectsConcurrentRequestsWithDifferentPayloadOnSameIdempotencyKey() throws Exception {
        // given: 같은 멱등성 키지만 서로 다른 payload를 가진 두 요청이 동시에 들어온다
        List<RegistrationOutcome> outcomes = runConcurrently(2, new ConcurrentRegistration[] {
                () -> registerAndMapOutcome(
                        "same-key-event-1",
                        "user-1",
                        NotificationType.ORDER,
                        NotificationChannel.EMAIL,
                        "첫 번째 제목",
                        "첫 번째 본문"
                ),
                () -> registerAndMapOutcome(
                        "same-key-event-1",
                        "user-1",
                        NotificationType.ORDER,
                        NotificationChannel.EMAIL,
                        "두 번째 제목",
                        "두 번째 본문"
                )
        });

        // then: 원본과 dispatch는 1건만 남고 다른 payload 요청은 mismatch로 거절된다
        assertThat(countOutcome(outcomes, RegistrationOutcome.SUCCESS)).isEqualTo(1);
        assertThat(countOutcome(outcomes, RegistrationOutcome.PAYLOAD_MISMATCH)).isEqualTo(1);
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(notificationDispatchRepository.count()).isEqualTo(1);
    }

    private RegistrationOutcome registerAndMapOutcome(
            String eventId,
            String recipientId,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String content
    ) {
        try {
            NotificationCreateRes response = notificationRegistrationService.register(
                    eventId,
                    recipientId,
                    type,
                    channel,
                    title,
                    content
            );
            assertThat(response.notificationId()).isNotNull();
            return RegistrationOutcome.SUCCESS;
        } catch (IdempotencyPayloadMismatchException exception) {
            return RegistrationOutcome.PAYLOAD_MISMATCH;
        } catch (DuplicateNotificationRequestException exception) {
            return RegistrationOutcome.DUPLICATE;
        }
    }

    private List<RegistrationOutcome> runConcurrently(int requestCount, ConcurrentRegistration registration)
            throws InterruptedException {
        ConcurrentRegistration[] registrations = new ConcurrentRegistration[requestCount];

        for (int i = 0; i < requestCount; i++) {
            registrations[i] = registration;
        }

        return runConcurrently(requestCount, registrations);
    }

    private List<RegistrationOutcome> runConcurrently(int requestCount, ConcurrentRegistration[] registrations)
            throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        List<RegistrationOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < requestCount; i++) {
                ConcurrentRegistration registration = registrations[i];
                executorService.submit(() -> {
                    readyLatch.countDown();

                    try {
                        startLatch.await();
                        outcomes.add(registration.run());
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executorService.shutdownNow();
        }

        assertThat(failures).isEmpty();
        return outcomes;
    }

    private long countOutcome(List<RegistrationOutcome> outcomes, RegistrationOutcome target) {
        return outcomes.stream()
                .filter(outcome -> outcome == target)
                .count();
    }

    @FunctionalInterface
    private interface ConcurrentRegistration {
        RegistrationOutcome run();
    }

    private enum RegistrationOutcome {
        SUCCESS,
        DUPLICATE,
        PAYLOAD_MISMATCH
    }
}
