package com.zivdah.order.serviceImpl;

import com.zivdah.common.event.OrderCreatedEvent;
import com.zivdah.order.dto.OrderItemDto;
import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.dto.OrderResponseDto;
import com.zivdah.order.entity.Order;
import com.zivdah.order.entity.OrderItem;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.repository.OrderRepository;
import com.zivdah.order.service.OrderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import com.zivdah.order.kafka.OrderKafkaProducer;
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderKafkaProducer orderKafkaProducer;

    @Override
    public OrderResponseDto createOrder(OrderRequestDto dto) {
        List<OrderItem> items = dto.getItems().stream().map(i ->
                OrderItem.builder()
                        .productId(i.getProductId())
                        .quantity(i.getQuantity())
                        .price(i.getPrice())
                        .subtotal(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .build()
        ).collect(Collectors.toList());

        BigDecimal total = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userId(dto.getUserId())
                .totalAmount(total)
                .status(OrderStatus.CREATED)
                .build();

        items.forEach(item -> item.setOrder(order));
        order.setItems(items);

        Order saved = orderRepository.save(order);

        orderKafkaProducer.publishOrderCreated(
                OrderCreatedEvent.builder()
                        .orderId(saved.getId())
                        .userId(saved.getUserId())
                        .totalAmount(saved.getTotalAmount())
                        .build()
        );
        log.info("OrderCreatedEvent published for orderId={}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    public OrderResponseDto getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    @Override
    public List<OrderResponseDto> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    private OrderResponseDto mapToResponse(Order order) {
        return OrderResponseDto.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .items(order.getItems().stream().map(i ->
                        OrderItemDto.builder()
                                .productId(i.getProductId())
                                .quantity(i.getQuantity())
                                .price(i.getPrice())
                                .build()
                ).collect(Collectors.toList()))
                .build();
    }
}
