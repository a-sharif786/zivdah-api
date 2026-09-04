package com.zivdah.auth.repository;

import com.zivdah.auth.entity.DeviceToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeviceTokenRepository extends ReactiveCrudRepository<DeviceToken, Long> {
    Mono<DeviceToken> findByFcmToken(String fcmToken);
    Flux<DeviceToken> findByUserIdAndIsActiveTrue(Long userId);
    Mono<Void> deleteByFcmToken(String fcmToken);
}
