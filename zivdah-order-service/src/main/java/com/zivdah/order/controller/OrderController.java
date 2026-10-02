package com.zivdah.order.controller;

import com.zivdah.common.security.InternalAuth;
import com.zivdah.order.client.DeliveryServiceClient;
import com.zivdah.order.dto.ApiResponse;
import com.zivdah.order.dto.DeliveryStatusSyncDto;
import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.dto.OrderResponseDto;
import com.zivdah.order.dto.OrderStatsResponseDto;
import com.zivdah.order.dto.OrderStatusUpdateRequestDto;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
    private final DeliveryServiceClient deliveryServiceClient;

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

    // Order access for reads: the internal service token (delivery/notification/chat/payment-service
    // lookups), ADMIN, the order's own customer, a VENDOR with an item on it (same rule as
    // InvoiceController#requireOrderAccess), or a DELIVERY_BOY assigned to it (the delivery-boy
    // portal hydrates each of its deliveries through this endpoint). Everyone else gets the same
    // "Order not found" as a genuinely missing order, so order ids can't be probed. Previously
    // GET /{orderId} was permitAll — anyone could read any order's delivery address.
    private Mono<OrderResponseDto> requireOrderAccess(OrderResponseDto order, String authorization) {
        return Mono.zip(currentUserPrincipal(), currentRole())
                .flatMap(t -> {
                    String principal = t.getT1();
                    String role = t.getT2();
                    if (InternalAuth.ROLE.equals(role) || "ADMIN".equalsIgnoreCase(role)
                            || principal.equals(String.valueOf(order.getUserId()))) {
                        return Mono.just(true);
                    }
                    if ("VENDOR".equalsIgnoreCase(role)) {
                        return Mono.just(order.getItems().stream()
                                .anyMatch(i -> principal.equals(String.valueOf(i.getVendorId()))));
                    }
                    if ("DELIVERY_BOY".equalsIgnoreCase(role)) {
                        return deliveryServiceClient.isAssignedToCaller(order.getOrderId(), bearerToken(authorization));
                    }
                    return Mono.just(false);
                })
                .filter(Boolean::booleanValue)
                .map(allowed -> order)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")));
    }

    private Mono<String> currentUserPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(Authentication::getName);
    }

    private static String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
    }

    @PostMapping("/create")
    public Mono<ResponseEntity<ApiResponse<OrderResponseDto>>> createOrder(
            @RequestBody OrderRequestDto dto) {
        return currentUserId()
                .flatMap(userId -> orderService.createOrder(dto, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.<OrderResponseDto>builder()
                        .status("success").statusCode(200).message("Order created successfully").data(r).build()));
    }

    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<ApiResponse<OrderResponseDto>>> getOrder(
            @PathVariable Long orderId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return orderService.getOrderById(orderId)
                .flatMap(order -> requireOrderAccess(order, authorization))
                .map(r -> ResponseEntity.ok(ApiResponse.<OrderResponseDto>builder()
                        .status("success").statusCode(200).message("Order retrieved successfully").data(r).build()));
    }

    // A user's own order history (storefront, Flutter, chat-service forwarding the user's token)
    // or ADMIN. Previously any authenticated user could list any other user's orders.
    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<ApiResponse<List<OrderResponseDto>>>> getOrdersByUser(@PathVariable Long userId) {
        return Mono.zip(currentUserPrincipal(), currentRole())
                .filter(t -> t.getT1().equals(String.valueOf(userId))
                        || "ADMIN".equalsIgnoreCase(t.getT2()) || InternalAuth.ROLE.equals(t.getT2()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Not authorized to view this user's orders")))
                .flatMap(t -> orderService.getOrdersByUser(userId).collectList())
                .map(list -> ResponseEntity.ok(ApiResponse.<List<OrderResponseDto>>builder()
                        .status("success").statusCode(200).message("Orders retrieved successfully").data(list).build()));
    }

    @PutMapping("/cancel/{orderId}")
    public Mono<ResponseEntity<ApiResponse<Void>>> cancelOrder(@PathVariable Long orderId) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> orderService.cancelOrder(orderId, t.getT1(), t.getT2()))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Order cancelled successfully").build()));
    }

    // Admin/vendor-facing lifecycle transition (CONFIRMED, PACKING, DELIVERED, REFUNDED, ...),
    // validated against the allowed-transition map in OrderServiceImpl.
    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public Mono<ResponseEntity<ApiResponse<OrderResponseDto>>> updateStatus(
            @PathVariable Long orderId, @RequestBody OrderStatusUpdateRequestDto dto) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> orderService.updateStatus(orderId, dto.getStatus(), t.getT1(), t.getT2()))
                .map(r -> ResponseEntity.ok(ApiResponse.<OrderResponseDto>builder()
                        .status("success").statusCode(200).message("Order status updated").data(r).build()));
    }

    // Internal, payment-service-only transition (CREATED -> PAID / CANCELLED). Kept separate
    // from the admin/vendor endpoint above since it's called service-to-service with no user
    // JWT — see SecurityConfig, which permits only this exact path without authentication.
    @PutMapping("/{orderId}/payment-status")
    public Mono<ResponseEntity<ApiResponse<Void>>> updatePaymentStatus(
            @PathVariable Long orderId, @RequestBody OrderStatusUpdateRequestDto dto) {
        return orderService.updatePaymentStatus(
                        orderId, dto.getStatus(), dto.getPaymentMethod(), dto.getTransactionId(), dto.getPaidAt())
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Order payment status updated").build()));
    }

    // Internal, delivery-service-only sync — no user JWT available for this call (see
    // SecurityConfig, same pattern as /payment-status above). Best-effort: an unrecognized
    // deliveryStatus is silently ignored server-side, never a 4xx.
    @PutMapping("/{orderId}/delivery-status")
    public Mono<ResponseEntity<ApiResponse<Void>>> syncDeliveryStatus(
            @PathVariable Long orderId, @RequestBody DeliveryStatusSyncDto dto) {
        return orderService.syncDeliveryStatus(orderId, dto.getDeliveryStatus())
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Order delivery status synced").build()));
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
