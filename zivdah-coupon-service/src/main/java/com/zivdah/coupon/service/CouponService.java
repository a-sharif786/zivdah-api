package com.zivdah.coupon.service;

import com.zivdah.coupon.dto.ApplyCouponRequestDto;
import com.zivdah.coupon.dto.ApplyCouponResponseDto;
import com.zivdah.coupon.dto.CouponRequestDto;
import com.zivdah.coupon.dto.CouponResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CouponService {
    Mono<CouponResponseDto> createCoupon(CouponRequestDto dto);
    Mono<CouponResponseDto> getCouponByCode(String code);
    Flux<CouponResponseDto> getAllCoupons();
    Mono<CouponResponseDto> toggleActive(Long couponId);
    Mono<Void> deleteCoupon(Long couponId);
    Mono<ApplyCouponResponseDto> applyCoupon(ApplyCouponRequestDto dto);
}
