package com.notification.mid.notification.application.policy;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    public Optional<LocalDateTime> nextRetryAt(int nextRetryCount, LocalDateTime now) {
        return switch (nextRetryCount) {
            case 1 -> Optional.of(now.plusMinutes(1));
            case 2 -> Optional.of(now.plusMinutes(5));
            case 3 -> Optional.of(now.plusMinutes(15));
            default -> Optional.empty();
        };
    }
}
