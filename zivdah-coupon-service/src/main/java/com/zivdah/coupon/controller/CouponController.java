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
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/coupons")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CouponResponseDto>>> createCoupon(
            @Valid @RequestBody CouponRequestDto dto) {
        return couponService.createCoupon(dto)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.<CouponResponseDto>builder()
                                .status("success").statusCode(201).message("Coupon created").data(r).build()));
    }

    @GetMapping("/{code}")
    public Mono<ResponseEntity<ApiResponse<CouponResponseDto>>> getCoupon(@PathVariable String code) {
        return couponService.getCouponByCode(code)
                .map(r -> ResponseEntity.ok(ApiResponse.<CouponResponseDto>builder()
                        .status("success").statusCode(200).data(r).build()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<CouponResponseDto>>>> getAllCoupons() {
        return couponService.getAllCoupons()
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<CouponResponseDto>>builder()
                        .status("success").statusCode(200).data(list).build()));
    }

    @PutMapping("/{couponId}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CouponResponseDto>>> toggleActive(@PathVariable Long couponId) {
        return couponService.toggleActive(couponId)
                .map(r -> ResponseEntity.ok(ApiResponse.<CouponResponseDto>builder()
                        .status("success").statusCode(200).data(r).build()));
    }

    @DeleteMapping("/{couponId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteCoupon(@PathVariable Long couponId) {
        return couponService.deleteCoupon(couponId)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Coupon deleted").build()));
    }

    @PostMapping("/apply")
    public Mono<ResponseEntity<ApiResponse<ApplyCouponResponseDto>>> applyCoupon(
            @Valid @RequestBody ApplyCouponRequestDto dto) {
        return couponService.applyCoupon(dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<ApplyCouponResponseDto>builder()
                        .status("success").statusCode(200).message(r.getMessage()).data(r).build()));
    }
}
