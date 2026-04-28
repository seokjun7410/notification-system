package com.notification.mid.notification.domain.shared;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class BaseEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    /**
     * 신규 엔티티 생성 시점의 공통 메타 정보를 한 번에 초기화한다.
     * 도메인 팩토리 메서드는 이 메서드를 호출해 생성/수정 시각과 삭제 플래그를 함께 세팅한다.
     */
    protected void initializeCreatedAtAndUpdatedAt(LocalDateTime now) {
        this.createdAt = now;
        this.updatedAt = now;
        this.deletedAt = null;
        this.isDeleted = false;
    }

    /**
     * 상태 변경이나 읽음 처리처럼 엔티티가 갱신됐을 때 수정 시각만 반영한다.
     */
    protected void updateUpdatedAt(LocalDateTime now) {
        this.updatedAt = now;
    }

    /**
     * 소프트 삭제 메타 정보를 갱신한다.
     * 현재 과제에서 직접 사용되지는 않지만, 삭제가 필요한 확장 요구사항에 공통 규칙으로 활용할 수 있다.
     */
    protected void markDeleted(LocalDateTime now) {
        this.deletedAt = now;
        this.isDeleted = true;
        updateUpdatedAt(now);
    }
}
