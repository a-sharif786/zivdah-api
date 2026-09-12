package com.zivdah.coupon.repository;

import com.zivdah.coupon.entity.Coupon;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CouponRepository extends ReactiveCrudRepository<Coupon, Long> {
    Mono<Coupon> findByCode(String code);
    // OrderByCreatedAtDesc: admin coupon list should show new coupons on top.
    Flux<Coupon> findAllByOrderByCreatedAtDesc();
}
