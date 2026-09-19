package com.zivdah.payment.dto;

import com.zivdah.payment.enums.VendorPayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class VendorPayoutResponseDto {
    private Long payoutId;
    private Long vendorId;
    private BigDecimal amount;
    private String payoutMode;
    private String accountNo;
    private String ifscBankCode;
    private String payeeVpa;
    private VendorPayoutStatus status;
    private String gatewayStatus;
    private String gatewayDescription;
    private String utrNumber;
    private String rejectionReason;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private LocalDateTime settledAt;
}
