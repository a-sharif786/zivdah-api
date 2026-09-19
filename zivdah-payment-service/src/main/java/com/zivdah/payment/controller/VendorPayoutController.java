package com.zivdah.payment.controller;

import com.zivdah.payment.dto.ApiResponse;
import com.zivdah.payment.dto.PayoutRequestDto;
import com.zivdah.payment.dto.RejectPayoutDto;
import com.zivdah.payment.dto.VendorPayoutResponseDto;
import com.zivdah.payment.enums.VendorPayoutStatus;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayPayoutResponse;
import com.zivdah.payment.service.VendorPayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/payments/payouts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VendorPayoutController {

    private final VendorPayoutService payoutService;

    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> Long.valueOf(ctx.getAuthentication().getName()));
    }

    private static String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "0.0.0.0";
    }

    @PostMapping
    @PreAuthorize("hasRole('VENDOR')")
    public Mono<ResponseEntity<ApiResponse<VendorPayoutResponseDto>>> requestPayout(
            @Valid @RequestBody PayoutRequestDto dto) {
        return currentUserId()
                .flatMap(vendorId -> payoutService.requestPayout(vendorId, dto.getAmount()))
                .map(r -> ResponseEntity.ok(ApiResponse.<VendorPayoutResponseDto>builder()
                        .status("success").statusCode(200).message("Payout requested").data(r).build()));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('VENDOR')")
    public Mono<ResponseEntity<ApiResponse<List<VendorPayoutResponseDto>>>> myPayouts() {
        return currentUserId()
                .flatMapMany(payoutService::getPayoutsForVendor)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<VendorPayoutResponseDto>>builder()
                        .status("success").statusCode(200).message("Payouts retrieved").data(list).build()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<VendorPayoutResponseDto>>>> allPayouts(
            @RequestParam(required = false) VendorPayoutStatus status) {
        return payoutService.getAllPayouts(status)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<VendorPayoutResponseDto>>builder()
                        .status("success").statusCode(200).message("Payouts retrieved").data(list).build()));
    }

    @PutMapping("/{payoutId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<VendorPayoutResponseDto>>> approve(
            @PathVariable Long payoutId, ServerHttpRequest request) {
        return payoutService.approvePayout(payoutId, clientIp(request))
                .map(r -> ResponseEntity.ok(ApiResponse.<VendorPayoutResponseDto>builder()
                        .status("success").statusCode(200).message("Payout submitted to gateway").data(r).build()));
    }

    @PutMapping("/{payoutId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<VendorPayoutResponseDto>>> reject(
            @PathVariable Long payoutId, @Valid @RequestBody RejectPayoutDto dto) {
        return payoutService.rejectPayout(payoutId, dto.getReason())
                .map(r -> ResponseEntity.ok(ApiResponse.<VendorPayoutResponseDto>builder()
                        .status("success").statusCode(200).message("Payout rejected").data(r).build()));
    }

    @GetMapping("/{payoutId}/refresh-status")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<VendorPayoutResponseDto>>> refreshStatus(@PathVariable Long payoutId) {
        return payoutService.refreshPayoutStatus(payoutId)
                .map(r -> ResponseEntity.ok(ApiResponse.<VendorPayoutResponseDto>builder()
                        .status("success").statusCode(200).message("Payout status refreshed").data(r).build()));
    }

    // No auth — EcomWorldPay's own webhook, same reasoning as PaymentController's
    // /callback/ecomworldpay (see payment-service SecurityConfig).
    @PostMapping("/callback")
    public Mono<ResponseEntity<ApiResponse<Void>>> callback(@RequestBody EcomWorldPayPayoutResponse callback) {
        return payoutService.handlePayoutCallback(callback)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Callback processed").build()));
    }
}
