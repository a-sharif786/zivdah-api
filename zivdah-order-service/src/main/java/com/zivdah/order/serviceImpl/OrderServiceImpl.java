package com.zivdah.order.serviceImpl;

import com.zivdah.common.event.OrderCreatedEvent;
import com.zivdah.order.dto.OrderItemDto;
import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.dto.OrderResponseDto;
import com.zivdah.order.entity.Order;
import com.zivdah.order.entity.OrderItem;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.kafka.OrderKafkaProducer;
import com.zivdah.order.repository.OrderItemRepository;
import com.zivdah.order.repository.OrderRepository;
import com.zivdah.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderKafkaProducer orderKafkaProducer;

    @Override
    public Mono<OrderResponseDto> createOrder(OrderRequestDto dto) {
        BigDecimal total = dto.getItems().stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userId(dto.getUserId()).totalAmount(total).status(OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .deliveryAddressLine1(dto.getDeliveryAddressLine1())
                .deliveryAddressLine2(dto.getDeliveryAddressLine2())
                .deliveryCity(dto.getDeliveryCity())
                .deliveryState(dto.getDeliveryState())
                .deliveryPinCode(dto.getDeliveryPinCode())
                .build();

        return orderRepository.save(order)
                .flatMap(savedOrder ->
                        Flux.fromIterable(dto.getItems())
                                .flatMap(i -> orderItemRepository.save(OrderItem.builder()
                                        .orderId(savedOrder.getId())
                                        .productId(i.getProductId())
                                        .quantity(i.getQuantity())
                                        .price(i.getPrice())
                                        .subtotal(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                                        .build()))
                                .collectList()
                                .map(savedItems -> {
                                    List<com.zivdah.common.dto.OrderItemDto> eventItems = savedItems.stream()
                                            .map(si -> com.zivdah.common.dto.OrderItemDto.builder()
                                                    .productId(si.getProductId())
                                                    .quantity(si.getQuantity())
                                                    .price(si.getPrice())
                                                    .build())
                                            .collect(Collectors.toList());
                                    orderKafkaProducer.publishOrderCreated(
                                            OrderCreatedEvent.builder()
                                                    .orderId(savedOrder.getId())
                                                    .userId(savedOrder.getUserId())
                                                    .totalAmount(savedOrder.getTotalAmount())
                                                    .items(eventItems)
                                                    .build());
                                    log.info("Order {} created, event published", savedOrder.getId());
                                    return mapToResponse(savedOrder, savedItems);
                                })
                );
    }

    @Override
    public Mono<OrderResponseDto> getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId)))
                .flatMap(order -> orderItemRepository.findByOrderId(order.getId())
                        .collectList()
                        .map(items -> mapToResponse(order, items)));
    }

    @Override
    public Flux<OrderResponseDto> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId)
                .flatMap(order -> orderItemRepository.findByOrderId(order.getId())
                        .collectList()
                        .map(items -> mapToResponse(order, items)));
    }

    @Override
    public Mono<Void> cancelOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId)))
                .flatMap(order -> {
                    order.setStatus(OrderStatus.CANCELLED);
                    return orderRepository.save(order);
                })
                .then();
    }

    private OrderResponseDto mapToResponse(Order order, List<OrderItem> items) {
        return OrderResponseDto.builder()
                .orderId(order.getId()).userId(order.getUserId())
                .totalAmount(order.getTotalAmount()).status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .deliveryAddressLine1(order.getDeliveryAddressLine1())
                .deliveryAddressLine2(order.getDeliveryAddressLine2())
                .deliveryCity(order.getDeliveryCity())
                .deliveryState(order.getDeliveryState())
                .deliveryPinCode(order.getDeliveryPinCode())
                .items(items.stream().map(i -> OrderItemDto.builder()
                        .productId(i.getProductId())
                        .quantity(i.getQuantity())
                        .price(i.getPrice())
                        .build()).collect(Collectors.toList()))
                .build();
    }
}
