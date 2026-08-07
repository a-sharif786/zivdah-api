package com.zivdah.user.repository;

import com.zivdah.user.entity.UserAddress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserAddressRepository extends ReactiveCrudRepository<UserAddress, Long> {
    Mono<Long> countByUserId(Long userId);
    Flux<UserAddress> findByUserId(Long userId, Pageable pageable);

    @Modifying
    @Query("UPDATE user_addresses SET is_default = false WHERE user_id = :userId")
    Mono<Integer> resetDefaultAddress(Long userId);
}
