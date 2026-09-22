package com.zivdah.payment.service;

import com.zivdah.payment.dto.VendorPayoutResponseDto;
import com.zivdah.payment.enums.PayoutMode;
import com.zivdah.payment.enums.VendorPayoutStatus;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayPayoutResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface VendorPayoutService {
    Mono<VendorPayoutResponseDto> requestPayout(Long vendorId, BigDecimal amount);

    // Admin-initiated payout for a chosen vendor, using that vendor's saved bank/UPI details —
    // same REQUESTED-then-approve flow as requestPayout, just with an explicit mode and a
    // different initiator recorded. Never calls the gateway directly; see approvePayout.
    Mono<VendorPayoutResponseDto> initiateAdminPayout(Long adminId, Long vendorId, BigDecimal amount, PayoutMode payoutMode);

    Flux<VendorPayoutResponseDto> getPayoutsForVendor(Long vendorId);

    // status == null returns every payout regardless of status.
    Flux<VendorPayoutResponseDto> getAllPayouts(VendorPayoutStatus status);

    // ipAddress is the admin's own request IP — EcomWorldPay's Payout API requires an
    // originating IP address per its docs.
    Mono<VendorPayoutResponseDto> approvePayout(Long payoutId, String ipAddress);
    Mono<VendorPayoutResponseDto> rejectPayout(Long payoutId, String reason);
    Mono<VendorPayoutResponseDto> refreshPayoutStatus(Long payoutId);

    // Webhook entry point — silently no-ops (rather than erroring) if the callback's
    // TransactionId doesn't match a known payout, so a gateway retry storm can't result from
    // an assumption mismatch (see VendorPayout's gatewayReferenceId doc comment).
    Mono<Void> handlePayoutCallback(EcomWorldPayPayoutResponse callback);
}
