package com.zivdah.chat.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Narrow projection of coupon-service's CouponResponseDto — only what the OFFERS intent needs
// to summarize an active coupon in one line (code, description, discount).
@Getter
@Setter
public class CouponSummaryDto {
    private String code;
    private String description;
    // Raw DiscountType enum name as a String ("PERCENTAGE" or "FLAT") — see OrderSummaryDto's
    // note on why these narrow DTOs use String instead of duplicating another service's enum.
    private String discountType;
    private BigDecimal discountValue;
}
