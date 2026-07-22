package com.zivdah.coupon.service;

import com.zivdah.coupon.dto.ApplyCouponRequestDto;
import com.zivdah.coupon.dto.ApplyCouponResponseDto;
import com.zivdah.coupon.dto.CouponRequestDto;
import com.zivdah.coupon.dto.CouponResponseDto;

import java.util.List;

public interface CouponService {
    CouponResponseDto createCoupon(CouponRequestDto dto);
    CouponResponseDto getCouponByCode(String code);
    List<CouponResponseDto> getAllCoupons();
    CouponResponseDto toggleActive(Long couponId);
    void deleteCoupon(Long couponId);
    ApplyCouponResponseDto applyCoupon(ApplyCouponRequestDto dto);
}
