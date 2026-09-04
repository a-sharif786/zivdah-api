package com.zivdah.payment.controller;

import com.zivdah.payment.dto.ApiResponse;
import com.zivdah.payment.dto.PaymentRequestDto;
import com.zivdah.payment.dto.PaymentResponseDto;
import com.zivdah.payment.dto.PaymentStatsResponseDto;
import com.zivdah.payment.enums.PaymentStatus;
import com.zivdah.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> initiatePayment(
            @RequestBody PaymentRequestDto dto) {
        return paymentService.initiatePayment(dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Payment initiated").data(r).build()));
    }

    @GetMapping("/{paymentId}")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> getPayment(@PathVariable Long paymentId) {
        return paymentService.getPayment(paymentId)
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Payment retrieved").data(r).build()));
    }

    @GetMapping("/order/{orderId}")
    public Mono<ResponseEntity<ApiResponse<List<PaymentResponseDto>>>> getPaymentsByOrder(
            @PathVariable Long orderId) {
        return paymentService.getPaymentsByOrder(orderId)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<PaymentResponseDto>>builder()
                        .status("success").statusCode(200).message("Payments retrieved").data(list).build()));
    }

    @PutMapping("/success/{paymentId}")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> markSuccess(@PathVariable Long paymentId) {
        return paymentService.markPaymentSuccess(paymentId)
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Payment marked as successful").data(r).build()));
    }

    @PutMapping("/failed/{paymentId}")
    public Mono<ResponseEntity<ApiResponse<PaymentResponseDto>>> markFailed(@PathVariable Long paymentId) {
        return paymentService.markPaymentFailed(paymentId)
                .map(r -> ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                        .status("success").statusCode(200).message("Payment marked as failed").data(r).build()));
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
}
