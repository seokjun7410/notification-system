package com.notification.mid.notification.application.port.in;

import com.notification.mid.notification.application.dto.NotificationDispatchTargetDto;
import java.util.List;

public interface NotificationDispatchService {

    /**
     * 현재 시점에 처리 가능한 dispatch를 점유하고 worker/consumer가 처리할 payload를 반환한다.
     */
    List<NotificationDispatchTargetDto> claimDispatchTargets(int batchSize);

    /**
     * 점유된 dispatch 한 건을 실제 발송 처리한다.
     */
    void dispatch(NotificationDispatchTargetDto dispatchTarget);
}
