package com.zivdah.coupon.controller;

import com.zivdah.coupon.dto.ApiResponse;
import com.zivdah.coupon.dto.ApplyCouponRequestDto;
import com.zivdah.coupon.dto.ApplyCouponResponseDto;
import com.zivdah.coupon.dto.CouponRequestDto;
import com.zivdah.coupon.dto.CouponResponseDto;
import com.zivdah.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/coupons")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponResponseDto>> createCoupon(@Valid @RequestBody CouponRequestDto dto) {
        CouponResponseDto response = couponService.createCoupon(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CouponResponseDto>builder()
                        .status("success").statusCode(201)
                        .message("Coupon created").data(response).build());
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<CouponResponseDto>> getCoupon(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.<CouponResponseDto>builder()
                .status("success").statusCode(200)
                .data(couponService.getCouponByCode(code)).build());
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CouponResponseDto>>> getAllCoupons() {
        return ResponseEntity.ok(ApiResponse.<List<CouponResponseDto>>builder()
                .status("success").statusCode(200)
                .data(couponService.getAllCoupons()).build());
    }

    @PutMapping("/{couponId}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponResponseDto>> toggleActive(@PathVariable Long couponId) {
        return ResponseEntity.ok(ApiResponse.<CouponResponseDto>builder()
                .status("success").statusCode(200)
                .data(couponService.toggleActive(couponId)).build());
    }

    @DeleteMapping("/{couponId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable Long couponId) {
        couponService.deleteCoupon(couponId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status("success").statusCode(200)
                .message("Coupon deleted").build());
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<ApplyCouponResponseDto>> applyCoupon(
            @Valid @RequestBody ApplyCouponRequestDto dto) {
        ApplyCouponResponseDto result = couponService.applyCoupon(dto);
        return ResponseEntity.ok(ApiResponse.<ApplyCouponResponseDto>builder()
                .status("success").statusCode(200)
                .message(result.getMessage()).data(result).build());
    }
}
