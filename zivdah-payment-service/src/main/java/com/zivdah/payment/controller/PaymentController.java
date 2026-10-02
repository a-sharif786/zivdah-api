package com.zivdah.payment.controller;

import com.zivdah.common.security.InternalAuth;
import com.zivdah.payment.dto.ApiResponse;
import com.zivdah.payment.dto.LinkOrderRequestDto;
import com.zivdah.payment.dto.PaymentRequestDto;
import com.zivdah.payment.dto.PaymentResponseDto;
import com.zivdah.payment.dto.PaymentStatsResponseDto;
import com.zivdah.payment.dto.RefundRequestDto;
import com.zivdah.payment.enums.PaymentStatus;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayTransactionDto;
import com.zivdah.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    // Caller identity comes only from the security context (JWT or internal token), never from the
    // request body. For an internal-service caller the principal isn't a user id, hence null.
    private record Caller(Long userId, boolean privileged) {}

    private Mono<Caller> caller() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> {
                    boolean privileged = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                            .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_" + InternalAuth.ROLE));
                    Long userId = auth.getName() != null && auth.getName().matches("\\d+") ? Long.valueOf(auth.getName()) : null;
                    return new Caller(userId, privileged);
                });
    }

    // A real (numeric) user id — initiate/link are storefront actions, not something an internal
    // service or a token without a userId claim can do.
    private Mono<Long> currentUserId() {
        return caller()
                .filter(c -> c.userId() != null)
                .map(Caller::userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "A user login is required")));
    }

    @PostMapping("/initiate")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> initiatePayment(
            @RequestBody PaymentRequestDto dto) {
        return currentUserId()
                .flatMap(userId -> paymentService.initiatePayment(dto, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Payment initiated").data(r).build()));
    }

    // Attaches the real orderId to a payment intent that was validated/created before the order
    // existed (see PaymentServiceImpl#initiatePayment) — called by the frontend right after
    // orderApi.create() succeeds. Idempotent no-op if already linked to this same orderId.
    // Verifies ownership of both sides and that the payment amount equals the order total.
    @PutMapping("/{paymentId}/link-order")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> linkOrder(
            @PathVariable Long paymentId, @RequestBody LinkOrderRequestDto dto) {
        return currentUserId()
                .flatMap(userId -> paymentService.linkOrder(paymentId, dto.getOrderId(), userId))
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Order linked to payment").data(r).build()));
    }

    // The payment's own customer (checkout polling) or ADMIN (payment detail page).
    @GetMapping("/{paymentId}")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> getPayment(@PathVariable Long paymentId) {
        return caller()
                .flatMap(c -> paymentService.getPayment(paymentId, c.userId(), c.privileged()))
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Payment retrieved").data(r).build()));
    }

    // ADMIN / internal services (order-service, chat-service) see all of the order's payments;
    // anyone else only their own.
    @GetMapping("/order/{orderId}")
    public Mono<ResponseEntity<ApiResponse<List<PaymentResponseDto>>>> getPaymentsByOrder(
            @PathVariable Long orderId) {
        return caller()
                .flatMap(c -> paymentService.getPaymentsByOrder(orderId, c.userId(), c.privileged()).collectList())
                .map(list -> ResponseEntity.ok(ApiResponse.<List<PaymentResponseDto>>builder()
                        .status("success").statusCode(200).message("Payments retrieved").data(list).build()));
    }

    // Manual override (e.g. COD cash collected) — ADMIN only. Previously any logged-in customer
    // could call this and mark their own (or anyone's) payment successful, and the storefront
    // itself did so for non-UPI methods.
    @PutMapping("/success/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> markSuccess(@PathVariable Long paymentId) {
        return paymentService.markPaymentSuccess(paymentId)
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Payment marked as successful").data(r).build()));
    }

    @PutMapping("/failed/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> markFailed(@PathVariable Long paymentId) {
        return paymentService.markPaymentFailed(paymentId)
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Payment marked as failed").data(r).build()));
    }

    @PutMapping("/refund/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> refund(
            @PathVariable Long paymentId, @RequestBody RefundRequestDto dto) {
        return paymentService.refundPayment(paymentId, dto.getAmount())
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Payment refunded").data(r).build()));
    }

    // Internal, order-service-only sync — authenticated by the internal service token (see
    // SecurityConfig), not a user JWT. Fully refunds this order's payment; a no-op if there's
    // nothing left to refund.
    @PutMapping("/order/{orderId}/refund")
    @PreAuthorize("hasRole('SERVICE')")
    public Mono<ResponseEntity<ApiResponse<Void>>> refundByOrder(@PathVariable Long orderId) {
        return paymentService.refundByOrder(orderId)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Order payment refunded").build()));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<PaymentStatsResponseDto>>> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return paymentService.getStats(from, to)
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentStatsResponseDto>builder()
                        .status("success").statusCode(200).message("Payment stats retrieved").data(r).build()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<PaymentResponseDto>>>> getAllPayments(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) PaymentStatus status) {
        return paymentService.getAllPayments(PageRequest.of(page, size), status)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<PaymentResponseDto>>builder()
                        .status("success").statusCode(200).message("Payments retrieved").data(list).build()));
    }

    @PostMapping("/callback/ecomworldpay")
    public Mono<ResponseEntity<ApiResponse<Void>>> ecomWorldPayCallback(@RequestBody EcomWorldPayTransactionDto callback) {
        return paymentService.handleGatewayCallback(callback)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Callback processed").build()));
    }

    // Active poll against EcomWorldPay's Transaction Status API, for when the async callback
    // above is missed or delayed.
    @GetMapping("/{paymentId}/gateway-status")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> refreshGatewayStatus(@PathVariable Long paymentId) {
        return caller()
                .flatMap(c -> paymentService.refreshGatewayStatus(paymentId, c.userId(), c.privileged()))
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Gateway status refreshed").data(r).build()));
    }
}
