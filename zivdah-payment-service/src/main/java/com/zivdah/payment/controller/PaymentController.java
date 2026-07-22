package com.zivdah.payment.controller;

import com.zivdah.payment.dto.ApiResponse;
import com.zivdah.payment.dto.PaymentRequestDto;
import com.zivdah.payment.dto.PaymentResponseDto;
import com.zivdah.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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
}
