package com.zivdah.coupon.repository;

import com.zivdah.coupon.entity.Coupon;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface CouponRepository extends ReactiveCrudRepository<Coupon, Long> {
    Mono<Coupon> findByCode(String code);
}
