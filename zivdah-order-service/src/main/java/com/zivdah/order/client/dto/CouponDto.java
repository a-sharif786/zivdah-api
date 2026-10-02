package com.zivdah.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// The subset of coupon-service's CouponResponseDto that order pricing needs (see
// OrderPricingService#couponDiscount). discountType is kept as a String ("PERCENTAGE"/"FIXED")
// rather than a copy of coupon-service's enum.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponDto {
    private String code;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private boolean active;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
}
