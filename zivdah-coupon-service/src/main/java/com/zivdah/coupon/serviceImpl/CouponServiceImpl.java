package com.zivdah.coupon.serviceImpl;

import com.zivdah.coupon.dto.ApplyCouponRequestDto;
import com.zivdah.coupon.dto.ApplyCouponResponseDto;
import com.zivdah.coupon.dto.CouponRequestDto;
import com.zivdah.coupon.dto.CouponResponseDto;
import com.zivdah.coupon.entity.Coupon;
import com.zivdah.coupon.enums.DiscountType;
import com.zivdah.coupon.repository.CouponRepository;
import com.zivdah.coupon.service.CouponService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    @Override
    public CouponResponseDto createCoupon(CouponRequestDto dto) {
        if (couponRepository.findByCode(dto.getCode()).isPresent()) {
            throw new RuntimeException("Coupon code already exists: " + dto.getCode());
        }
        Coupon coupon = Coupon.builder()
                .code(dto.getCode().toUpperCase())
                .description(dto.getDescription())
                .discountType(dto.getDiscountType())
                .discountValue(dto.getDiscountValue())
                .minOrderAmount(dto.getMinOrderAmount())
                .maxDiscountAmount(dto.getMaxDiscountAmount())
                .usageLimit(dto.getUsageLimit())
                .usedCount(0)
                .active(true)
                .validFrom(dto.getValidFrom())
                .validUntil(dto.getValidUntil())
                .build();
        return mapToDto(couponRepository.save(coupon));
    }

    @Override
    public CouponResponseDto getCouponByCode(String code) {
        return couponRepository.findByCode(code.toUpperCase())
                .map(this::mapToDto)
                .orElseThrow(() -> new RuntimeException("Coupon not found: " + code));
    }

    @Override
    public List<CouponResponseDto> getAllCoupons() {
        return couponRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public CouponResponseDto toggleActive(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new RuntimeException("Coupon not found: " + couponId));
        coupon.setActive(!coupon.isActive());
        return mapToDto(couponRepository.save(coupon));
    }

    @Override
    public void deleteCoupon(Long couponId) {
        couponRepository.findById(couponId)
                .orElseThrow(() -> new RuntimeException("Coupon not found: " + couponId));
        couponRepository.deleteById(couponId);
    }

    @Override
    public ApplyCouponResponseDto applyCoupon(ApplyCouponRequestDto dto) {
        Coupon coupon = couponRepository.findByCode(dto.getCode().toUpperCase())
                .orElseThrow(() -> new RuntimeException("Coupon not found: " + dto.getCode()));

        LocalDateTime now = LocalDateTime.now();

        if (!coupon.isActive()) {
            throw new RuntimeException("Coupon is inactive");
        }
        if (now.isBefore(coupon.getValidFrom())) {
            throw new RuntimeException("Coupon is not yet valid");
        }
        if (now.isAfter(coupon.getValidUntil())) {
            throw new RuntimeException("Coupon has expired");
        }
        if (coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new RuntimeException("Coupon usage limit reached");
        }
        if (coupon.getMinOrderAmount() != null
                && dto.getOrderAmount().compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new RuntimeException("Order amount is below the minimum required: " + coupon.getMinOrderAmount());
        }

        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = dto.getOrderAmount()
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = coupon.getDiscountValue();
        }

        if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discount = coupon.getMaxDiscountAmount();
        }

        BigDecimal finalAmount = dto.getOrderAmount().subtract(discount).max(BigDecimal.ZERO);

        coupon.setUsedCount(coupon.getUsedCount() + 1);
        couponRepository.save(coupon);

        log.info("Coupon {} applied: discount={}, final={}", dto.getCode(), discount, finalAmount);

        return ApplyCouponResponseDto.builder()
                .couponCode(coupon.getCode())
                .originalAmount(dto.getOrderAmount())
                .discountAmount(discount)
                .finalAmount(finalAmount)
                .message("Coupon applied successfully")
                .build();
    }

    private CouponResponseDto mapToDto(Coupon coupon) {
        return CouponResponseDto.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .description(coupon.getDescription())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minOrderAmount(coupon.getMinOrderAmount())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .usageLimit(coupon.getUsageLimit())
                .usedCount(coupon.getUsedCount())
                .active(coupon.isActive())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .createdAt(coupon.getCreatedAt())
                .build();
    }
}
