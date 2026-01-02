package com.zivdah.payment.controller;

import com.zivdah.payment.dto.*;
import com.zivdah.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> initiatePayment(
            @RequestBody PaymentRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Payment initiated successfully")
                .data(paymentService.initiatePayment(dto))
                .build());
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Payment retrieved successfully")
                .data(paymentService.getPayment(paymentId))
                .build());
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<List<PaymentResponseDto>>> getPaymentsByOrder(
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.<List<PaymentResponseDto>>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Payments retrieved successfully")
                .data(paymentService.getPaymentsByOrder(orderId))
                .build());
    }

    @PutMapping("/success/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> markSuccess(@PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Payment marked as successful")
                .data(paymentService.markPaymentSuccess(paymentId))
                .build());
    }

    @PutMapping("/failed/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> markFailed(@PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.<PaymentResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Payment marked as failed")
                .data(paymentService.markPaymentFailed(paymentId))
                .build());
    }
}
