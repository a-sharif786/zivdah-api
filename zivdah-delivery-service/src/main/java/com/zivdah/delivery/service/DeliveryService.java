package com.zivdah.delivery.service;

import com.zivdah.delivery.dto.DeliveryResponseDto;
import com.zivdah.delivery.enums.DeliveryStatus;
import com.zivdah.delivery.enums.FailureReason;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeliveryService {

    // Called by OrderEventConsumer when an order reaches CONFIRMED — one PENDING row per
    // vendor on the order. Idempotent: a re-fired event for a (orderId, vendorId) pair that
    // already has a row is a no-op (see DeliveryRepository#findByOrderIdAndVendorId).
    Mono<Void> createPendingDeliveriesForOrder(Long orderId, Long userId);

    // ADMIN, or the owning VENDOR (delivery.vendorId == currentUserId).
    Mono<DeliveryResponseDto> assignDeliveryBoy(Long deliveryId, Long deliveryBoyId, Long currentUserId, String role);

    // Role-scoped transition — ADMIN may set anything valid from the current status; VENDOR
    // is restricted to PACKED/READY_FOR_PICKUP/CANCELLED on their own deliveries;
    // DELIVERY_BOY is restricted to PICKED_UP/ON_THE_WAY/DELIVERED/FAILED on deliveries
    // assigned to them. FAILED requires failureReason.
    Mono<DeliveryResponseDto> updateStatus(Long deliveryId, DeliveryStatus newStatus,
                                            FailureReason failureReason, String failureNote,
                                            Long currentUserId, String role);

    // Visibility-filtered: ADMIN sees every vendor-portion; VENDOR/DELIVERY_BOY see only
    // their own; the customer (any other authenticated role) sees only their own order.
    Flux<DeliveryResponseDto> getByOrder(Long orderId, Long currentUserId, String role);

    Flux<DeliveryResponseDto> getByVendor(Long vendorId, Pageable pageable, Long currentUserId, String role);

    Flux<DeliveryResponseDto> getMyDeliveries(Long deliveryBoyId, Pageable pageable);
}
