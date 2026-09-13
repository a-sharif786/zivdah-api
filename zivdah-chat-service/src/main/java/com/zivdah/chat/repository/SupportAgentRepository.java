package com.zivdah.chat.repository;

import com.zivdah.chat.entity.SupportAgent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface SupportAgentRepository extends ReactiveCrudRepository<SupportAgent, Long> {

    // user_id is UNIQUE — used to check whether an ADMIN is also a roster'd support agent
    // (Phase 6: agent-scoped queue endpoints, /agents CRUD).
    Mono<SupportAgent> findByUserId(Long userId);
}
