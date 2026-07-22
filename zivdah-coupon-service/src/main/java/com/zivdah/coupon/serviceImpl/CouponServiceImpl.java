package com.zivdah.coupon.serviceImpl;

import com.zivdah.coupon.dto.ApplyCouponRequestDto;
import com.zivdah.coupon.dto.ApplyCouponResponseDto;
import com.zivdah.coupon.dto.CouponRequestDto;
import com.zivdah.coupon.dto.CouponResponseDto;
import com.zivdah.coupon.entity.Coupon;
import com.zivdah.coupon.enums.DiscountType;
import com.zivdah.coupon.repository.CouponRepository;
import com.zivdah.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    @Override
    public Mono<CouponResponseDto> createCoupon(CouponRequestDto dto) {
        return couponRepository.findByCode(dto.getCode().toUpperCase())
                .flatMap(existing -> Mono.<CouponResponseDto>error(
                        new ResponseStatusException(HttpStatus.CONFLICT, "Coupon code already exists: " + dto.getCode())))
                .switchIfEmpty(Mono.defer(() -> {
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
                            .createdAt(LocalDateTime.now())
                            .build();
                    return couponRepository.save(coupon).map(this::mapToDto);
                }));
    }

    @Override
    public Mono<CouponResponseDto> getCouponByCode(String code) {
        return couponRepository.findByCode(code.toUpperCase())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found: " + code)))
                .map(this::mapToDto);
    }

    @Override
    public Flux<CouponResponseDto> getAllCoupons() {
        return couponRepository.findAll().map(this::mapToDto);
    }

    @Override
    public Mono<CouponResponseDto> toggleActive(Long couponId) {
        return couponRepository.findById(couponId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found: " + couponId)))
                .flatMap(coupon -> {
                    coupon.setActive(!coupon.isActive());
                    return couponRepository.save(coupon);
                })
                .map(this::mapToDto);
    }

    @Override
    public Mono<Void> deleteCoupon(Long couponId) {
        return couponRepository.existsById(couponId)
                .flatMap(exists -> {
                    if (!exists) return Mono.<Void>error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found: " + couponId));
                    return couponRepository.deleteById(couponId);
                });
    }

    @Override
    public Mono<ApplyCouponResponseDto> applyCoupon(ApplyCouponRequestDto dto) {
        return couponRepository.findByCode(dto.getCode().toUpperCase())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found: " + dto.getCode())))
                .flatMap(coupon -> {
                    LocalDateTime now = LocalDateTime.now();
                    if (!coupon.isActive()) return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon is inactive"));
                    if (now.isBefore(coupon.getValidFrom())) return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon not yet valid"));
                    if (now.isAfter(coupon.getValidUntil())) return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon has expired"));
                    if (coupon.getUsedCount() >= coupon.getUsageLimit()) return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon usage limit reached"));
                    if (coupon.getMinOrderAmount() != null && dto.getOrderAmount().compareTo(coupon.getMinOrderAmount()) < 0)
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order below minimum: " + coupon.getMinOrderAmount()));

                    BigDecimal discount = coupon.getDiscountType() == DiscountType.PERCENTAGE
                            ? dto.getOrderAmount().multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                            : coupon.getDiscountValue();
                    if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0)
                        discount = coupon.getMaxDiscountAmount();
                    BigDecimal finalAmount = dto.getOrderAmount().subtract(discount).max(BigDecimal.ZERO);

                    coupon.setUsedCount(coupon.getUsedCount() + 1);
                    BigDecimal finalDiscount = discount;
                    return couponRepository.save(coupon)
                            .map(saved -> {
                                log.info("Coupon {} applied: discount={}, final={}", dto.getCode(), finalDiscount, finalAmount);
                                return ApplyCouponResponseDto.builder()
                                        .couponCode(saved.getCode())
                                        .originalAmount(dto.getOrderAmount())
                                        .discountAmount(finalDiscount)
                                        .finalAmount(finalAmount)
                                        .message("Coupon applied successfully")
                                        .build();
                            });
                });
    }

    private CouponResponseDto mapToDto(Coupon c) {
        return CouponResponseDto.builder()
                .id(c.getId()).code(c.getCode()).description(c.getDescription())
                .discountType(c.getDiscountType()).discountValue(c.getDiscountValue())
                .minOrderAmount(c.getMinOrderAmount()).maxDiscountAmount(c.getMaxDiscountAmount())
                .usageLimit(c.getUsageLimit()).usedCount(c.getUsedCount()).active(c.isActive())
                .validFrom(c.getValidFrom()).validUntil(c.getValidUntil()).createdAt(c.getCreatedAt())
                .build();
    }
}
