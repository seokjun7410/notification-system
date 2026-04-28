
# 사용자 알림 조회 성능 개선

## 1. 사용자 알림 조회 요구사항

사용자 알림 목록은 수신자 기준으로 조회합니다.

조회 대상은 사용자에게 실제로 노출 가능한 알림만 포함합니다.

- 채널: `IN_APP`
- 발송 상태: `SUCCESS`
- 읽음/안읽음 필터 지원
- cursor 기반 페이지네이션 적용

---

## 2. 조회 방식

현재 사용자 알림 목록 조회는 두 단계로 나누어 수행합니다.

1. 조건에 맞는 `notification id`만 먼저 조회
2. 조회된 id 범위에 대해서만 `notification` 본문을 batch 조회

```sql
-- 1) 대상 notification id 선별
select cast(n.id as varchar)
from notification n
join notification_dispatch d
  on d.notification_id = n.id
 and d.channel = 'IN_APP'
 and d.status = 'SUCCESS'
where n.recipient_id = :recipientId
  and (
      :readFilter is null
      or (:readFilter = true and d.read_at is not null)
      or (:readFilter = false and d.read_at is null)
  )
  and (
      :cursorCreatedAt is null
      or n.created_at < :cursorCreatedAt
      or (n.created_at = :cursorCreatedAt and cast(n.id as varchar) < :cursorId)
  )
order by n.created_at desc, n.id desc
limit :limit;

-- 2) 본문 notification batch 조회
select *
from notification
where id in (:notificationIds);
````

---

## 3. 이 구조를 선택한 이유

알림 목록 조회에서 가장 중요한 부분은 `수신자 기준 정렬 + 페이지 처리`입니다.

만약 큰 테이블에서 본문 컬럼까지 한 번에 조회하면서 정렬과 페이지 처리를 수행하면, 불필요하게 많은 데이터를 읽고 정렬할 수 있습니다.

따라서 먼저 정렬과 cursor 조건에 필요한 `notification id`만 선별하고, 이후 제한된 개수의 알림 본문만 다시 조회하도록 분리했습니다.

이 방식의 장점은 다음과 같습니다.

* 큰 테이블에서 본문 컬럼까지 한 번에 읽는 비용을 줄일 수 있습니다.
* 정렬과 cursor 조건에 필요한 데이터만 먼저 탐색할 수 있습니다.
* 실제 응답에 필요한 본문은 이미 제한된 범위에서만 조회할 수 있습니다.
* 목록 조회와 본문 조회의 책임이 분리되어 성능 분석이 쉬워집니다.

---

## 4. 성능 측정 조건

성능 측정은 다음 조건에서 진행했습니다.

* 수신자 1명 기준
* `notification` 100만 건
* `notification_dispatch` 100만 건
* `notification_attempt` 300만 건
* Postman 기준 응답 시간 측정

---

## 5. 성능 측정 결과

| 단계 | 조회 방식                                                                          | 응답 시간  |
| -- | ------------------------------------------------------------------------------ | ------ |
| 1  | no-index + offset pagination                                                   | 14.46s |
| 2  | no-index + cursor pagination                                                   | 11.84s |
| 3  | cursor pagination + `notification(recipient_id, created_at desc, id desc)` 인덱스 | 3.84s  |

---

## 6. 결과 해석

- offset pagination을 cursor pagination으로 변경하면서 뒤 페이지로 갈수록 커지는 skip 비용을 줄일 수 있었습니다.
- 다만 인덱스가 없는 상태에서는 cursor pagination을 적용하더라도 여전히 대량 범위 탐색 비용이 남아 있었습니다.
- 가장 큰 성능 개선은 `notification(recipient_id, created_at desc, id desc)` 복합 인덱스를 추가한 뒤 발생했습니다.
- 이 인덱스를 통해 수신자 기준 필터링, 생성일 기준 정렬, 동일 생성일 내 id 정렬을 함께 처리할 수 있었고, 그 결과 조회 시간이 크게 감소했습니다.
