package com.zivdah.auth.repository;

import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.enums.Role;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<UserEntity, Long> {
    Mono<UserEntity> findByEmail(String email);
    Mono<Boolean> existsByEmail(String email);
    Mono<Boolean> existsByMobile(String mobile);
    Mono<UserEntity> findByMobile(String mobile);
    Mono<Long> countByRole(Role role);
}
