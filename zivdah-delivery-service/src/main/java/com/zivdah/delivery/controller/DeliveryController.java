package com.zivdah.delivery.controller;

import com.zivdah.delivery.dto.ApiResponse;
import com.zivdah.delivery.dto.AssignDeliveryBoyRequestDto;
import com.zivdah.delivery.dto.DeliveryResponseDto;
import com.zivdah.delivery.dto.DeliveryStatusUpdateRequestDto;
import com.zivdah.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/delivery")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DeliveryController {

    private final DeliveryService deliveryService;

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

    @PostMapping("/{deliveryId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public Mono<ResponseEntity<ApiResponse<DeliveryResponseDto>>> assign(
            @PathVariable Long deliveryId, @Valid @RequestBody AssignDeliveryBoyRequestDto dto) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> deliveryService.assignDeliveryBoy(deliveryId, dto.getDeliveryBoyId(), t.getT1(), t.getT2()))
                .map(r -> ResponseEntity.ok(ApiResponse.<DeliveryResponseDto>builder()
                        .status("success").statusCode(200).message("Delivery boy assigned").data(r).build()));
    }

    // Role-scoped transition — see DeliveryService#updateStatus for exactly which target
    // statuses each role may set.
    @PatchMapping("/{deliveryId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR','DELIVERY_BOY')")
    public Mono<ResponseEntity<ApiResponse<DeliveryResponseDto>>> updateStatus(
            @PathVariable Long deliveryId, @Valid @RequestBody DeliveryStatusUpdateRequestDto dto) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> deliveryService.updateStatus(
                        deliveryId, dto.getStatus(), dto.getFailureReason(), dto.getFailureNote(), t.getT1(), t.getT2()))
                .map(r -> ResponseEntity.ok(ApiResponse.<DeliveryResponseDto>builder()
                        .status("success").statusCode(200).message("Delivery status updated").data(r).build()));
    }

    // Visibility-filtered server-side (see DeliveryService#getByOrder) — the customer who
    // placed the order, the vendor(s) on it, and any admin can all call this same endpoint
    // and each only see what's theirs to see.
    @GetMapping("/order/{orderId}")
    public Mono<ResponseEntity<ApiResponse<List<DeliveryResponseDto>>>> getByOrder(@PathVariable Long orderId) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> deliveryService.getByOrder(orderId, t.getT1(), t.getT2()).collectList())
                .map(list -> ResponseEntity.ok(ApiResponse.<List<DeliveryResponseDto>>builder()
                        .status("success").statusCode(200).message("Deliveries retrieved").data(list).build()));
    }

    @GetMapping("/vendor/{vendorId}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public Mono<ResponseEntity<ApiResponse<List<DeliveryResponseDto>>>> getByVendor(
            @PathVariable Long vendorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> deliveryService.getByVendor(vendorId, PageRequest.of(page, size), t.getT1(), t.getT2()).collectList())
                .map(list -> ResponseEntity.ok(ApiResponse.<List<DeliveryResponseDto>>builder()
                        .status("success").statusCode(200).message("Vendor deliveries retrieved").data(list).build()));
    }

    // A delivery boy's own assigned deliveries — no path id needed, scoped to the caller.
    @GetMapping("/my")
    @PreAuthorize("hasRole('DELIVERY_BOY')")
    public Mono<ResponseEntity<ApiResponse<List<DeliveryResponseDto>>>> getMyDeliveries(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return currentUserId()
                .flatMap(id -> deliveryService.getMyDeliveries(id, PageRequest.of(page, size)).collectList())
                .map(list -> ResponseEntity.ok(ApiResponse.<List<DeliveryResponseDto>>builder()
                        .status("success").statusCode(200).message("My deliveries retrieved").data(list).build()));
    }
}
