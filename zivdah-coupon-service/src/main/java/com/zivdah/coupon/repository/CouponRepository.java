package com.zivdah.coupon.repository;

import com.zivdah.coupon.entity.Coupon;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface CouponRepository extends ReactiveCrudRepository<Coupon, Long> {
    Mono<Coupon> findByCode(String code);
    // OrderByCreatedAtDesc: admin coupon list should show new coupons on top.
    Flux<Coupon> findAllByOrderByCreatedAtDesc();
    // Backs GET /coupons/active — the only public "what offers are available" listing endpoint.
    // now1/now2 are the same instant, passed twice: active AND already-started AND not-yet-ended.
    Flux<Coupon> findByActiveTrueAndValidFromBeforeAndValidUntilAfter(LocalDateTime now1, LocalDateTime now2);
}
