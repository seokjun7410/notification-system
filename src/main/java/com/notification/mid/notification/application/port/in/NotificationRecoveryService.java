package com.notification.mid.notification.application.port.in;

public interface NotificationRecoveryService {

    /**
     * 오래된 PROCESSING 상태의 dispatch를 UNKNOWN으로 전환한다.
     */
    int recoverStaleProcessingDispatches(int batchSize);
}
