package com.zivdah.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Vendor-submitted payout request body — the amount is entered manually by the vendor, not
// computed from any "unpaid revenue" bookkeeping (there isn't any yet).
@Getter
@Setter
public class PayoutRequestDto {
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;
}
