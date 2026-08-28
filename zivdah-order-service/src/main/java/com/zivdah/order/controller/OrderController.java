package com.zivdah.order.controller;

import com.zivdah.order.dto.ApiResponse;
import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.dto.OrderResponseDto;
import com.zivdah.order.dto.OrderStatsResponseDto;
import com.zivdah.order.dto.OrderStatusUpdateRequestDto;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;

    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(Authentication::getName)
                .map(Long::valueOf);
    }

    private Mono<String> currentRole() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(auth -> auth.getAuthorities().stream().findFirst()
                        .map(GrantedAuthority::getAuthority)
                        .map(a -> a.replaceFirst("^ROLE_", ""))
                        .orElse(""));
    }

    @PostMapping("/create")
    public Mono<ResponseEntity<ApiResponse<OrderResponseDto>>> createOrder(
            @RequestBody OrderRequestDto dto) {
        return orderService.createOrder(dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<OrderResponseDto>builder()
                        .status("success").statusCode(200).message("Order created successfully").data(r).build()));
    }

    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<ApiResponse<OrderResponseDto>>> getOrder(@PathVariable Long orderId) {
        return orderService.getOrderById(orderId)
                .map(r -> ResponseEntity.ok(ApiResponse.<OrderResponseDto>builder()
                        .status("success").statusCode(200).message("Order retrieved successfully").data(r).build()));
    }

    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<ApiResponse<List<OrderResponseDto>>>> getOrdersByUser(@PathVariable Long userId) {
        return orderService.getOrdersByUser(userId)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<OrderResponseDto>>builder()
                        .status("success").statusCode(200).message("Orders retrieved successfully").data(list).build()));
    }

    @PutMapping("/cancel/{orderId}")
    public Mono<ResponseEntity<ApiResponse<Void>>> cancelOrder(@PathVariable Long orderId) {
        return orderService.cancelOrder(orderId)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Order cancelled successfully").build()));
    }

    // Admin/vendor-facing lifecycle transition (CONFIRMED, PACKING, DELIVERED, REFUNDED, ...),
    // validated against the allowed-transition map in OrderServiceImpl.
    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public Mono<ResponseEntity<ApiResponse<OrderResponseDto>>> updateStatus(
            @PathVariable Long orderId, @RequestBody OrderStatusUpdateRequestDto dto) {
        return orderService.updateStatus(orderId, dto.getStatus())
                .map(r -> ResponseEntity.ok(ApiResponse.<OrderResponseDto>builder()
                        .status("success").statusCode(200).message("Order status updated").data(r).build()));
    }

    // Internal, payment-service-only transition (CREATED -> PAID / CANCELLED). Kept separate
    // from the admin/vendor endpoint above since it's called service-to-service with no user
    // JWT — see SecurityConfig, which permits only this exact path without authentication.
    @PutMapping("/{orderId}/payment-status")
    public Mono<ResponseEntity<ApiResponse<Void>>> updatePaymentStatus(
            @PathVariable Long orderId, @RequestBody OrderStatusUpdateRequestDto dto) {
        return orderService.updatePaymentStatus(orderId, dto.getStatus())
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Order payment status updated").build()));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<OrderStatsResponseDto>>> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return orderService.getStats(from, to)
                .map(r -> ResponseEntity.ok(ApiResponse.<OrderStatsResponseDto>builder()
                        .status("success").statusCode(200).message("Order stats retrieved").data(r).build()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<OrderResponseDto>>>> getAllOrders(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) OrderStatus status) {
        return orderService.getAllOrders(PageRequest.of(page, size), status)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<OrderResponseDto>>builder()
                        .status("success").statusCode(200).message("Orders retrieved successfully").data(list).build()));
    }

    @GetMapping("/vendor/{vendorId}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public Mono<ResponseEntity<ApiResponse<List<OrderResponseDto>>>> getOrdersByVendor(
            @PathVariable Long vendorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> {
                    if ("VENDOR".equalsIgnoreCase(t.getT2()) && !t.getT1().equals(vendorId)) {
                        return Mono.<List<OrderResponseDto>>error(
                                new ResponseStatusException(HttpStatus.FORBIDDEN, "Vendors may only query their own orders"));
                    }
                    return orderService.getOrdersByVendor(vendorId, PageRequest.of(page, size)).collectList();
                })
                .map(list -> ResponseEntity.ok(ApiResponse.<List<OrderResponseDto>>builder()
                        .status("success").statusCode(200).message("Vendor orders retrieved successfully").data(list).build()));
    }
}
