package com.zivdah.order.service;

import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.dto.OrderResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderService {
    Mono<OrderResponseDto> createOrder(OrderRequestDto dto);
    Mono<OrderResponseDto> getOrderById(Long orderId);
    Flux<OrderResponseDto> getOrdersByUser(Long userId);
    Mono<Void> cancelOrder(Long orderId);
}
