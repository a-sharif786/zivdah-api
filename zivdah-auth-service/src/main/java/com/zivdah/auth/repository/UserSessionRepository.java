package com.zivdah.auth.repository;

import com.zivdah.auth.entity.UserSession;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserSessionRepository extends ReactiveCrudRepository<UserSession, Long> {
    Mono<UserSession> findByUserId(Long userId);
}
