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
    Mono<Void> cancelOrder(Long orderId);
    Flux<OrderResponseDto> getAllOrders(Pageable pageable, OrderStatus status);
    Flux<OrderResponseDto> getOrdersByVendor(Long vendorId, Pageable pageable);

    // Admin/vendor-driven lifecycle transition (CONFIRMED, PACKING, DELIVERED, REFUNDED, ...),
    // validated against the allowed-transition map.
    Mono<OrderResponseDto> updateStatus(Long orderId, OrderStatus newStatus);

    // Narrow, payment-service-only transition (CREATED -> PAID / CANCELLED). Kept separate
    // from updateStatus() since it's called internally, without an admin/vendor JWT.
    Mono<Void> updatePaymentStatus(Long orderId, OrderStatus newStatus);

    Mono<OrderStatsResponseDto> getStats(LocalDateTime from, LocalDateTime to);
}
