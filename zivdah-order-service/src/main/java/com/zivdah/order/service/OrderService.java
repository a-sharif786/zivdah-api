package com.zivdah.order.service;

import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.dto.OrderResponseDto;
import com.zivdah.order.dto.OrderStatsResponseDto;
import com.zivdah.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface OrderService {
    Mono<OrderResponseDto> createOrder(OrderRequestDto dto);
    Mono<OrderResponseDto> getOrderById(Long orderId);
    Flux<OrderResponseDto> getOrdersByUser(Long userId);
    Mono<Void> cancelOrder(Long orderId, Long currentUserId, String role);
    Flux<OrderResponseDto> getAllOrders(Pageable pageable, OrderStatus status);
    Flux<OrderResponseDto> getOrdersByVendor(Long vendorId, Pageable pageable);

    // Admin/vendor-driven lifecycle transition (CONFIRMED, PACKING, DELIVERED, REFUNDED, ...),
    // validated against the allowed-transition map. currentUserId/role are used for: a VENDOR
    // must own an item on the order; only ADMIN may set REFUNDED; and both are carried on the
    // published OrderStatusChangedEvent so notification-service can tell a vendor-initiated
    // CANCELLED (a "reject") apart from any other cancellation.
    Mono<OrderResponseDto> updateStatus(Long orderId, OrderStatus newStatus, Long currentUserId, String role);

    // Narrow, payment-service-only transition (CREATED -> PAID / CANCELLED). Kept separate
    // from updateStatus() since it's called internally, without an admin/vendor JWT.
    Mono<Void> updatePaymentStatus(Long orderId, OrderStatus newStatus);

    // Narrow, delivery-service-only sync (their "ON_THE_WAY" -> our OUT_FOR_DELIVERY, their
    // "DELIVERED" -> our DELIVERED) so this Order's status stays consistent with the more
    // granular Delivery sub-status the existing admin/vendor screens don't know about.
    // Best-effort: an unrecognized deliveryStatus is a silent no-op, not an error — Delivery
    // is the source of truth for fulfillment, this is just a convenience mirror of it.
    Mono<Void> syncDeliveryStatus(Long orderId, String deliveryStatus);

    Mono<OrderStatsResponseDto> getStats(LocalDateTime from, LocalDateTime to);
}
