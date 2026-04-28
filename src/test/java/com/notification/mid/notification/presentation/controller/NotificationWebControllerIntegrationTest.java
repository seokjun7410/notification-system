package com.notification.mid.notification.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.notification.mid.notification.domain.attempt.NotificationAttempt;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationAttemptRepository;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationDispatchRepository;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationRepository;
import com.notification.mid.notification.presentation.dto.request.NotificationCreateReq;
import com.notification.mid.notification.support.fixture.NotificationCreateReqFixture;
import com.notification.mid.notification.support.fixture.NotificationDispatchFixture;
import com.notification.mid.notification.support.fixture.NotificationFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NotificationWebControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("알림 등록 요청 시 notification과 dispatch를 생성하고 201 Created를 반환한다")
    void createsNotification() throws Exception {
        // given: 정상적인 알림 등록 요청이 있다
        NotificationCreateReq request = NotificationCreateReqFixture.createRequest();
        String requestBody = objectMapper.writeValueAsString(request);

        // when: 알림 등록 API를 호출하면
        var response = mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // then: notificationId와 dispatchId가 함께 생성된다
        response.andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.notificationId").isString())
                .andExpect(jsonPath("$.data.dispatchId").isString())
                .andExpect(jsonPath("$.data.channel").value("EMAIL"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("같은 type이지만 다른 channel이면 기존 notification을 재사용하고 새 dispatch를 만든다")
    void createsAdditionalDispatchWhenChannelChanges() throws Exception {
        // given: 같은 eventId, recipientId, type 조합의 EMAIL 요청이 먼저 등록되어 있다
        NotificationCreateReq emailRequest = NotificationCreateReqFixture.createRequest();
        NotificationCreateReq inAppRequest = NotificationCreateReqFixture.createRequest(
                "order-paid-10001",
                "user-1",
                NotificationChannel.IN_APP
        );

        String emailRequestBody = objectMapper.writeValueAsString(emailRequest);
        String inAppRequestBody = objectMapper.writeValueAsString(inAppRequest);

        MvcResult firstResult = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequestBody))
                .andExpect(status().isCreated())
                .andReturn();

        // when: 같은 알림을 다른 채널로 다시 등록하면
        MvcResult secondResult = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inAppRequestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.channel").value("IN_APP"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        JsonNode firstData = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("data");
        JsonNode secondData = objectMapper.readTree(secondResult.getResponse().getContentAsString()).get("data");

        // then: notification은 1건만 유지되고 dispatch만 2건이 된다
        assertThat(firstData.get("notificationId").asText()).isEqualTo(secondData.get("notificationId").asText());
        assertThat(firstData.get("dispatchId").asText()).isNotEqualTo(secondData.get("dispatchId").asText());
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(notificationDispatchRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 type과 channel 조합이면 409 Conflict를 반환한다")
    void returnsConflictWhenRequestIsDuplicated() throws Exception {
        // given: 이미 등록된 EMAIL 요청이 있다
        NotificationCreateReq request = NotificationCreateReqFixture.createRequest();
        String requestBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // when: 같은 채널 요청을 다시 보내면
        var response = mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // then: 중복 등록으로 409 응답을 반환한다
        response.andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATE_NOTIFICATION_REQUEST"))
                .andExpect(jsonPath("$.message").value("이미 등록된 알림 요청입니다."));
    }

    @Test
    @DisplayName("같은 멱등성 키에 다른 payload가 오면 409 Conflict를 반환한다")
    void returnsConflictWhenIdempotencyPayloadDoesNotMatch() throws Exception {
        // given: 기존 멱등성 키와 다른 payload를 가진 두 요청이 있다
        NotificationCreateReq firstRequest = NotificationCreateReqFixture.createRequest();
        NotificationCreateReq mismatchRequest = NotificationCreateReqFixture.createRequest(
                "order-paid-10001",
                "user-1",
                NotificationChannel.IN_APP,
                "수정된 제목",
                "수정된 본문"
        );

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // when: 같은 키로 다른 payload를 보내면
        var response = mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mismatchRequest)));

        // then: payload 충돌로 409 응답을 반환한다
        response.andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_PAYLOAD_MISMATCH"))
                .andExpect(jsonPath("$.message").value("같은 멱등성 키에 다른 payload를 보낼 수 없습니다."));

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(notificationDispatchRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("특정 알림 요청의 현재 상태 조회 시 원본 정보와 채널별 상태를 함께 반환한다")
    void getsNotificationDetail() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 25, 9, 0);
        LocalDateTime processingAt = createdAt.plusMinutes(1);
        LocalDateTime nextRetryAt = createdAt.plusMinutes(6);

        // given: 재시도 대기 EMAIL과 읽은 IN_APP dispatch를 가진 알림 원본이 있다
        Notification notification = notificationRepository.save(NotificationFixture.createNotification(
                "event-1",
                "user-1",
                NotificationType.ORDER,
                "title",
                "content",
                createdAt
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createRetryWaitDispatch(
                notification,
                NotificationChannel.EMAIL,
                createdAt,
                processingAt,
                1,
                nextRetryAt,
                "첫 번째 실패"
        ));
        var readDispatch = notificationDispatchRepository.save(NotificationDispatchFixture.createReadInAppDispatch(
                notification,
                createdAt.plusMinutes(1),
                createdAt.plusMinutes(2)
        ));
        var emailDispatch = notificationDispatchRepository.findByNotificationIdOrderByCreatedAtAscIdAsc(notification.getId()).getFirst();
        notificationAttemptRepository.save(NotificationAttempt.failure(
                emailDispatch,
                1,
                "첫 번째 실패",
                processingAt,
                processingAt.plusSeconds(5)
        ));
        notificationAttemptRepository.save(NotificationAttempt.success(
                readDispatch,
                1,
                createdAt.plusMinutes(1),
                createdAt.plusMinutes(1).plusSeconds(1)
        ));

        // when: 상태 조회 API를 호출하면
        var response = mockMvc.perform(get("/api/v1/notifications/{notificationId}", notification.getId()));

        // then: 원본 정보와 채널별 상태, 시도 이력을 함께 반환한다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.notificationId").value(notification.getId().toString()))
                .andExpect(jsonPath("$.data.dispatches.length()").value(2))
                .andExpect(jsonPath("$.data.dispatches[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$.data.dispatches[0].status").value("RETRY_WAIT"))
                .andExpect(jsonPath("$.data.dispatches[0].retryCount").value(1))
                .andExpect(jsonPath("$.data.dispatches[0].readStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.dispatches[0].attempts.length()").value(1))
                .andExpect(jsonPath("$.data.dispatches[0].attempts[0].attemptNo").value(1))
                .andExpect(jsonPath("$.data.dispatches[0].attempts[0].resultStatus").value("FAILURE"))
                .andExpect(jsonPath("$.data.dispatches[1].channel").value("IN_APP"))
                .andExpect(jsonPath("$.data.dispatches[1].status").value("PENDING"))
                .andExpect(jsonPath("$.data.dispatches[1].retryCount").value(0))
                .andExpect(jsonPath("$.data.dispatches[1].readStatus").value("READ"))
                .andExpect(jsonPath("$.data.dispatches[1].attempts.length()").value(1))
                .andExpect(jsonPath("$.data.dispatches[1].attempts[0].resultStatus").value("SUCCESS"));
    }

    @Test
    @DisplayName("없는 알림 ID 조회 시 404를 반환한다")
    void returnsNotFoundWhenNotificationDoesNotExist() throws Exception {
        // given: 존재하지 않는 알림 원본 ID가 있다

        // when: 상세 조회 API를 호출하면
        var response = mockMvc.perform(get("/api/v1/notifications/{notificationId}", UUID.randomUUID()));

        // then: 404 NOTIFICATION_NOT_FOUND를 반환한다
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("잘못된 notificationId 형식으로 상세 조회하면 400 INVALID_REQUEST를 반환한다")
    void returnsBadRequestWhenNotificationIdIsInvalid() throws Exception {
        // given: UUID 형식이 아닌 notificationId path variable이 있다

        // when: 상세 조회 API를 호출하면
        var response = mockMvc.perform(get("/api/v1/notifications/{notificationId}", "not-a-uuid"));

        // then: 400 INVALID_REQUEST를 반환한다
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("관리자 알림 목록 조회 시 notification 기준으로 묶고 dispatches 배열을 반환한다")
    void searchesAdminNotificationsByRecipient() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 25, 8, 0);

        // given: 한 알림은 두 채널을 가지고 다른 알림은 한 채널만 가진다
        Notification firstNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-1",
                "user-1",
                NotificationType.ORDER,
                "first",
                "content",
                createdAt
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createPendingDispatch(
                firstNotification,
                NotificationChannel.EMAIL,
                createdAt
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createPendingDispatch(
                firstNotification,
                NotificationChannel.IN_APP,
                createdAt.plusMinutes(1)
        ));

        Notification secondNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-2",
                "user-1",
                NotificationType.GENERAL,
                "second",
                "content",
                createdAt.plusMinutes(2)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createPendingDispatch(
                secondNotification,
                NotificationChannel.EMAIL,
                createdAt.plusMinutes(2)
        ));

        // when: 관리자 목록 조회 API를 호출하면
        var response = mockMvc.perform(get("/api/v1/admin/users/{recipientId}/notifications", "user-1")
                .queryParam("size", "20"));

        // then: notification 기준 목록과 채널별 dispatches 배열을 반환한다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.content[0].dispatches.length()").value(1))
                .andExpect(jsonPath("$.data.content[1].dispatches.length()").value(2));
    }

    @Test
    @DisplayName("관리자 알림 목록에서 channel 필터 조회 시 dispatches 배열에는 조건에 맞는 채널만 남는다")
    void filtersAdminDispatchesByChannel() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 25, 8, 0);

        // given: 같은 알림 원본에 EMAIL과 IN_APP dispatch가 함께 있다
        Notification notification = notificationRepository.save(NotificationFixture.createNotification(
                "event-1",
                "user-1",
                NotificationType.ORDER,
                "first",
                "content",
                createdAt
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createPendingDispatch(
                notification,
                NotificationChannel.EMAIL,
                createdAt
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createPendingDispatch(
                notification,
                NotificationChannel.IN_APP,
                createdAt.plusMinutes(1)
        ));

        // when: IN_APP 채널 필터로 조회하면
        var response = mockMvc.perform(get("/api/v1/admin/users/{recipientId}/notifications", "user-1")
                .queryParam("channel", "IN_APP")
                .queryParam("size", "20"));

        // then: dispatches 배열에는 IN_APP 채널만 남는다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.content[0].dispatches.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].dispatches[0].channel").value("IN_APP"));
    }

    @Test
    @DisplayName("관리자 알림 목록에서 잘못된 channel 형식이면 400 INVALID_REQUEST를 반환한다")
    void returnsBadRequestWhenAdminChannelIsInvalid() throws Exception {
        // given: 허용되지 않는 channel query param이 있다

        // when: 관리자 목록 조회 API를 호출하면
        var response = mockMvc.perform(get("/api/v1/admin/users/{recipientId}/notifications", "user-1")
                .queryParam("channel", "INVALID")
                .queryParam("size", "20"));

        // then: 400 INVALID_REQUEST를 반환한다
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("관리자 알림 목록에서 IN_APP 채널은 readStatus 필터로 읽지 않은 알림만 조회할 수 있다")
    void filtersUnreadInAppNotificationsForAdmin() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 25, 8, 0);

        // given: 읽은 IN_APP 알림과 읽지 않은 IN_APP 알림이 함께 있다
        Notification unreadNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-1",
                "user-1",
                NotificationType.ORDER,
                "first",
                "content",
                createdAt
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createPendingDispatch(
                unreadNotification,
                NotificationChannel.IN_APP,
                createdAt
        ));

        Notification readNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-2",
                "user-1",
                NotificationType.ORDER,
                "second",
                "content",
                createdAt.plusMinutes(1)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createReadInAppDispatch(
                readNotification,
                createdAt.plusMinutes(1),
                createdAt.plusMinutes(2)
        ));

        // when: 읽지 않은 IN_APP만 필터링하면
        var response = mockMvc.perform(get("/api/v1/admin/users/{recipientId}/notifications", "user-1")
                .queryParam("channel", "IN_APP")
                .queryParam("readStatus", "UNREAD")
                .queryParam("size", "20"));

        // then: 읽지 않은 알림만 목록에 남는다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.content[0].title").value("first"))
                .andExpect(jsonPath("$.data.content[0].dispatches[0].readStatus").value("UNREAD"));
    }

    @Test
    @DisplayName("관리자 알림 목록은 nextCursor로 다음 페이지를 이어서 조회할 수 있다")
    void paginatesAdminNotificationsByCursor() throws Exception {
        // given: 커서 페이지네이션 대상 알림 3건이 시간순으로 있다
        Notification newestNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-1",
                "user-1",
                NotificationType.ORDER,
                "first",
                "content",
                LocalDateTime.of(2026, 4, 25, 10, 0)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createPendingDispatch(
                newestNotification,
                NotificationChannel.EMAIL,
                newestNotification.getCreatedAt()
        ));

        Notification middleNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-2",
                "user-1",
                NotificationType.ORDER,
                "second",
                "content",
                LocalDateTime.of(2026, 4, 25, 9, 0)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createPendingDispatch(
                middleNotification,
                NotificationChannel.EMAIL,
                middleNotification.getCreatedAt()
        ));

        Notification oldestNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-3",
                "user-1",
                NotificationType.ORDER,
                "third",
                "content",
                LocalDateTime.of(2026, 4, 25, 8, 0)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createPendingDispatch(
                oldestNotification,
                NotificationChannel.EMAIL,
                oldestNotification.getCreatedAt()
        ));

        // when: 첫 페이지를 size 2로 조회하면
        MvcResult firstPageResult = mockMvc.perform(get("/api/v1/admin/users/{recipientId}/notifications", "user-1")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].title").value("first"))
                .andExpect(jsonPath("$.data.content[1].title").value("second"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isString())
                .andReturn();

        String nextCursor = objectMapper.readTree(firstPageResult.getResponse().getContentAsString())
                .get("data")
                .get("nextCursor")
                .asText();

        // then: nextCursor로 이어 조회하면 남은 1건만 반환된다
        mockMvc.perform(get("/api/v1/admin/users/{recipientId}/notifications", "user-1")
                        .queryParam("size", "2")
                        .queryParam("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("third"))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("관리자 알림 목록에서 cursor 형식이 잘못되면 400 INVALID_REQUEST를 반환한다")
    void returnsBadRequestWhenAdminCursorIsInvalid() throws Exception {
        // given: 형식이 잘못된 cursor가 있다

        // when: 관리자 목록 조회에 잘못된 cursor를 보내면
        var response = mockMvc.perform(get("/api/v1/admin/users/{recipientId}/notifications", "user-1")
                .queryParam("cursor", "not-a-valid-cursor"));

        // then: 400 INVALID_REQUEST를 반환한다
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("관리자 알림 목록에서 readStatus만 단독으로 조회하면 400 INVALID_REQUEST를 반환한다")
    void returnsBadRequestWhenAdminReadStatusIsUsedWithoutChannel() throws Exception {
        // given: channel 없이 readStatus만 전달하는 요청이 있다

        // when: 관리자 목록 조회 API를 호출하면
        var response = mockMvc.perform(get("/api/v1/admin/users/{recipientId}/notifications", "user-1")
                .queryParam("readStatus", "READ"));

        // then: 잘못된 필터 조합으로 400을 반환한다
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("관리자 알림 목록에서 EMAIL 채널에 readStatus 필터를 사용하면 400 INVALID_REQUEST를 반환한다")
    void returnsBadRequestWhenAdminEmailUsesReadStatus() throws Exception {
        // given: EMAIL 채널에 readStatus를 함께 준 요청이 있다

        // when: 관리자 목록 조회 API를 호출하면
        var response = mockMvc.perform(get("/api/v1/admin/users/{recipientId}/notifications", "user-1")
                .queryParam("channel", "EMAIL")
                .queryParam("readStatus", "READ"));

        // then: 허용되지 않은 필터 조합으로 400을 반환한다
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("사용자 알림 목록은 IN_APP 발송 완료 알림만 경량 응답으로 반환한다")
    void searchesUserInAppNotifications() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 25, 8, 0);

        // given: 성공한 IN_APP, 미완료 IN_APP, EMAIL 성공 알림이 함께 있다
        Notification unreadSuccessNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-1",
                "recipient-1",
                NotificationType.ORDER,
                "first",
                "content",
                createdAt
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createSuccessDispatch(
                unreadSuccessNotification,
                NotificationChannel.IN_APP,
                createdAt,
                createdAt.plusMinutes(1)
        ));

        Notification pendingNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-2",
                "recipient-1",
                NotificationType.ORDER,
                "pending",
                "content",
                createdAt.plusMinutes(1)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createPendingDispatch(
                pendingNotification,
                NotificationChannel.IN_APP,
                createdAt.plusMinutes(1)
        ));

        Notification emailOnlyNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-3",
                "recipient-1",
                NotificationType.GENERAL,
                "email-only",
                "content",
                createdAt.plusMinutes(2)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createSuccessDispatch(
                emailOnlyNotification,
                NotificationChannel.EMAIL,
                createdAt.plusMinutes(2),
                createdAt.plusMinutes(3)
        ));

        // when: 사용자 목록 조회 API를 호출하면
        var response = mockMvc.perform(get("/api/v1/users/{recipientId}/notifications", "recipient-1")
                .queryParam("size", "20"));

        // then: 성공한 IN_APP 알림만 경량 응답으로 반환한다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].eventId").value("event-1"))
                .andExpect(jsonPath("$.data.content[0].title").value("first"))
                .andExpect(jsonPath("$.data.content[0].dispatches").doesNotExist());
    }

    @Test
    @DisplayName("사용자 알림 목록은 readStatus로 읽지 않은 IN_APP 발송 완료 알림만 조회할 수 있다")
    void filtersUnreadUserInAppNotifications() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 25, 8, 0);

        // given: 읽은 성공 알림과 읽지 않은 성공 알림이 함께 있다
        Notification unreadNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-1",
                "recipient-1",
                NotificationType.ORDER,
                "first",
                "content",
                createdAt
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createSuccessDispatch(
                unreadNotification,
                NotificationChannel.IN_APP,
                createdAt,
                createdAt.plusMinutes(1)
        ));

        Notification readNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-2",
                "recipient-1",
                NotificationType.ORDER,
                "second",
                "content",
                createdAt.plusMinutes(1)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createSuccessReadInAppDispatch(
                readNotification,
                createdAt.plusMinutes(1),
                createdAt.plusMinutes(2),
                createdAt.plusMinutes(3)
        ));

        // when: UNREAD 필터로 사용자 목록을 조회하면
        var response = mockMvc.perform(get("/api/v1/users/{recipientId}/notifications", "recipient-1")
                .queryParam("readStatus", "UNREAD")
                .queryParam("size", "20"));

        // then: 읽지 않은 IN_APP 성공 알림만 남는다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("first"));
    }

    @Test
    @DisplayName("사용자 알림 목록은 nextCursor로 다음 페이지를 이어서 조회할 수 있다")
    void paginatesUserNotificationsByCursor() throws Exception {
        // given: 사용자 목록 커서 조회 대상 알림 3건이 있다
        Notification newestNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-1",
                "recipient-1",
                NotificationType.ORDER,
                "first",
                "content",
                LocalDateTime.of(2026, 4, 25, 10, 0)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createSuccessDispatch(
                newestNotification,
                NotificationChannel.IN_APP,
                newestNotification.getCreatedAt(),
                newestNotification.getCreatedAt().plusMinutes(1)
        ));

        Notification middleNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-2",
                "recipient-1",
                NotificationType.ORDER,
                "second",
                "content",
                LocalDateTime.of(2026, 4, 25, 9, 0)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createSuccessDispatch(
                middleNotification,
                NotificationChannel.IN_APP,
                middleNotification.getCreatedAt(),
                middleNotification.getCreatedAt().plusMinutes(1)
        ));

        Notification oldestNotification = notificationRepository.save(NotificationFixture.createNotification(
                "event-3",
                "recipient-1",
                NotificationType.ORDER,
                "third",
                "content",
                LocalDateTime.of(2026, 4, 25, 8, 0)
        ));
        notificationDispatchRepository.save(NotificationDispatchFixture.createSuccessDispatch(
                oldestNotification,
                NotificationChannel.IN_APP,
                oldestNotification.getCreatedAt(),
                oldestNotification.getCreatedAt().plusMinutes(1)
        ));

        // when: 첫 페이지를 size 2로 조회하면
        MvcResult firstPageResult = mockMvc.perform(get("/api/v1/users/{recipientId}/notifications", "recipient-1")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].title").value("first"))
                .andExpect(jsonPath("$.data.content[1].title").value("second"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isString())
                .andReturn();

        String nextCursor = objectMapper.readTree(firstPageResult.getResponse().getContentAsString())
                .get("data")
                .get("nextCursor")
                .asText();

        // then: nextCursor로 이어 조회하면 남은 1건만 반환된다
        mockMvc.perform(get("/api/v1/users/{recipientId}/notifications", "recipient-1")
                        .queryParam("size", "2")
                        .queryParam("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("third"))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("사용자 알림 목록에서 readStatus=UNKNOWN 이면 400 INVALID_REQUEST를 반환한다")
    void returnsBadRequestWhenUserReadStatusIsUnknown() throws Exception {
        // given: 허용되지 않는 UNKNOWN readStatus가 있다

        // when: 사용자 목록 조회 API를 호출하면
        var response = mockMvc.perform(get("/api/v1/users/{recipientId}/notifications", "recipient-1")
                .queryParam("readStatus", "UNKNOWN"));

        // then: 400 INVALID_REQUEST를 반환한다
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
