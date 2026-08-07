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
import com.zivdah.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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


        Order order = Order.builder()
                .userId(dto.getUserId())

                .orderNumber(generateOrderNumber())   // <-- Add this

                .subTotal(dto.getSubTotal())

                .gstAmount(dto.getGstAmount())
                .cgstAmount(dto.getCgstAmount())
                .sgstAmount(dto.getSgstAmount())
                .igstAmount(dto.getIgstAmount())
                .totalTaxAmount(dto.getTotalTaxAmount())

                .deliveryCharge(dto.getDeliveryCharge())
                .packagingCharge(dto.getPackagingCharge())
                .handlingCharge(dto.getHandlingCharge())

                .discountAmount(dto.getDiscountAmount())
                .couponCode(dto.getCouponCode())

                .totalAmount(dto.getTotalAmount())

                .currency(dto.getCurrency())

                .status(OrderStatus.CREATED)

                .deliveryAddressLine1(dto.getDeliveryAddressLine1())
                .deliveryAddressLine2(dto.getDeliveryAddressLine2())
                .deliveryCity(dto.getDeliveryCity())
                .deliveryState(dto.getDeliveryState())
                .deliveryPinCode(dto.getDeliveryPinCode())
                .deliveryCountry(dto.getDeliveryCountry())

                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())

                .build();

//        Order order = Order.builder()
//                .userId(dto.getUserId())
//
//                .subTotal(dto.getSubTotal())
//
//                .gstAmount(dto.getGstAmount())
//                .cgstAmount(dto.getCgstAmount())
//                .sgstAmount(dto.getSgstAmount())
//                .igstAmount(dto.getIgstAmount())
//                .totalTaxAmount(dto.getTotalTaxAmount())
//
//                .deliveryCharge(dto.getDeliveryCharge())
//                .packagingCharge(dto.getPackagingCharge())
//                .handlingCharge(dto.getHandlingCharge())
//
//                .discountAmount(dto.getDiscountAmount())
//                .couponCode(dto.getCouponCode())
//
//                .totalAmount(dto.getTotalAmount())
//
//                .currency(dto.getCurrency())
//
//                .status(OrderStatus.CREATED)
//
//                .deliveryAddressLine1(dto.getDeliveryAddressLine1())
//                .deliveryAddressLine2(dto.getDeliveryAddressLine2())
//                .deliveryCity(dto.getDeliveryCity())
//                .deliveryState(dto.getDeliveryState())
//                .deliveryPinCode(dto.getDeliveryPinCode())
//                .deliveryCountry(dto.getDeliveryCountry())
//
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//
//                .build();


        return orderRepository.save(order)

                .flatMap(savedOrder ->

                        Flux.fromIterable(dto.getItems())

                                .flatMap(item ->
                                        orderItemRepository.save(
                                                OrderItem.builder()
                                                        .orderId(savedOrder.getId())
                                                        .productId(item.getProductId())
                                                        .vendorId(item.getVendorId())
                                                        .quantity(item.getQuantity())
                                                        .price(item.getPrice())
                                                        .subtotal(
                                                                item.getPrice()
                                                                        .multiply(
                                                                                java.math.BigDecimal
                                                                                        .valueOf(item.getQuantity())
                                                                        )
                                                        )
                                                        .build()
                                        )
                                )

                                .collectList()

                                .map(savedItems -> {


                                    List<com.zivdah.common.dto.OrderItemDto> eventItems =
                                            savedItems.stream()
                                                    .map(item ->
                                                            com.zivdah.common.dto.OrderItemDto.builder()
                                                                    .productId(item.getProductId())
                                                                    .quantity(item.getQuantity())
                                                                    .price(item.getPrice())
                                                                    .build()
                                                    )
                                                    .collect(Collectors.toList());


                                    orderKafkaProducer.publishOrderCreated(
                                            OrderCreatedEvent.builder()
                                                    .orderId(savedOrder.getId())
                                                    .userId(savedOrder.getUserId())
                                                    .totalAmount(savedOrder.getTotalAmount())
                                                    .items(eventItems)
                                                    .build()
                                    );


                                    log.info(
                                            "Order {} created successfully",
                                            savedOrder.getId()
                                    );


                                    return mapToResponse(savedOrder, savedItems);
                                })
                );
    }



    @Override
    public Mono<OrderResponseDto> getOrderById(Long orderId) {

        return orderRepository.findById(orderId)

                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Order not found"
                                )
                        )
                )

                .flatMap(order ->
                        orderItemRepository
                                .findByOrderId(order.getId())
                                .collectList()
                                .map(items -> mapToResponse(order, items))
                );
    }


    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }



    @Override
    public Flux<OrderResponseDto> getOrdersByUser(Long userId) {

        return orderRepository.findByUserId(userId)

                .flatMap(order ->
                        orderItemRepository
                                .findByOrderId(order.getId())
                                .collectList()
                                .map(items -> mapToResponse(order, items))
                );
    }



    @Override
    public Mono<Void> cancelOrder(Long orderId) {

        return orderRepository.findById(orderId)

                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Order not found"
                                )
                        )
                )

                .flatMap(order -> {

                    order.setStatus(OrderStatus.CANCELLED);
                    order.setUpdatedAt(LocalDateTime.now());

                    return orderRepository.save(order);
                })

                .then();
    }



    @Override
    public Flux<OrderResponseDto> getAllOrders(Pageable pageable, OrderStatus status) {
        Flux<Order> orders = status != null
                ? orderRepository.findByStatus(status, pageable)
                : orderRepository.findAllBy(pageable);

        return orders.flatMap(order ->
                orderItemRepository.findByOrderId(order.getId())
                        .collectList()
                        .map(items -> mapToResponse(order, items))
        );
    }

    @Override
    public Flux<OrderResponseDto> getOrdersByVendor(Long vendorId, Pageable pageable) {
        return orderItemRepository.findByVendorId(vendorId, pageable)
                .collectList()
                .flatMapMany(vendorItems -> {
                    if (vendorItems.isEmpty()) {
                        return Flux.empty();
                    }
                    List<Long> orderIds = vendorItems.stream()
                            .map(OrderItem::getOrderId)
                            .distinct()
                            .collect(Collectors.toList());
                    Map<Long, List<OrderItem>> itemsByOrder = vendorItems.stream()
                            .collect(Collectors.groupingBy(OrderItem::getOrderId));

                    return orderRepository.findAllById(orderIds)
                            .map(order -> mapToResponse(order, itemsByOrder.get(order.getId())));
                });
    }

    private OrderResponseDto mapToResponse(
            Order order,
            List<OrderItem> items
    ) {


        return OrderResponseDto.builder()

                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())

                .userId(order.getUserId())

                .subTotal(order.getSubTotal())

                .gstAmount(order.getGstAmount())
                .cgstAmount(order.getCgstAmount())
                .sgstAmount(order.getSgstAmount())
                .igstAmount(order.getIgstAmount())
                .totalTaxAmount(order.getTotalTaxAmount())


                .deliveryCharge(order.getDeliveryCharge())
                .packagingCharge(order.getPackagingCharge())
                .handlingCharge(order.getHandlingCharge())


                .discountAmount(order.getDiscountAmount())
                .couponCode(order.getCouponCode())


                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())


                .status(order.getStatus())


                .deliveryAddressLine1(order.getDeliveryAddressLine1())
                .deliveryAddressLine2(order.getDeliveryAddressLine2())
                .deliveryCity(order.getDeliveryCity())
                .deliveryState(order.getDeliveryState())
                .deliveryPinCode(order.getDeliveryPinCode())
                .deliveryCountry(order.getDeliveryCountry())


                .items(
                        items.stream()
                                .map(item ->
                                        OrderItemDto.builder()
                                                .productId(item.getProductId())
                                                .vendorId(item.getVendorId())
                                                .quantity(item.getQuantity())
                                                .price(item.getPrice())
                                                .subtotal(item.getSubtotal())
                                                .build()
                                )
                                .collect(Collectors.toList())
                )


                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())

                .build();
    }
}
//package com.zivdah.order.serviceImpl;
//
//import com.zivdah.common.event.OrderCreatedEvent;
//import com.zivdah.order.dto.OrderItemDto;
//import com.zivdah.order.dto.OrderRequestDto;
//import com.zivdah.order.dto.OrderResponseDto;
//import com.zivdah.order.entity.Order;
//import com.zivdah.order.entity.OrderItem;
//import com.zivdah.order.enums.OrderStatus;
//import com.zivdah.order.kafka.OrderKafkaProducer;
//import com.zivdah.order.repository.OrderItemRepository;
//import com.zivdah.order.repository.OrderRepository;
//import com.zivdah.order.service.OrderService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpStatus;
//import org.springframework.stereotype.Service;
//import org.springframework.web.server.ResponseStatusException;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Service
//@Slf4j
//@RequiredArgsConstructor
//public class OrderServiceImpl implements OrderService {
//
//    private final OrderRepository orderRepository;
//    private final OrderItemRepository orderItemRepository;
//    private final OrderKafkaProducer orderKafkaProducer;
//
//    @Override
//    public Mono<OrderResponseDto> createOrder(OrderRequestDto dto) {
//        BigDecimal total = dto.getItems().stream()
//                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//        Order order = Order.builder()
//                .userId(dto.getUserId()).totalAmount(total).status(OrderStatus.CREATED)
//                .createdAt(LocalDateTime.now())
//                .deliveryAddressLine1(dto.getDeliveryAddressLine1())
//                .deliveryAddressLine2(dto.getDeliveryAddressLine2())
//                .deliveryCity(dto.getDeliveryCity())
//                .deliveryState(dto.getDeliveryState())
//                .deliveryPinCode(dto.getDeliveryPinCode())
//                .build();
//
//        return orderRepository.save(order)
//                .flatMap(savedOrder ->
//                        Flux.fromIterable(dto.getItems())
//                                .flatMap(i -> orderItemRepository.save(OrderItem.builder()
//                                        .orderId(savedOrder.getId())
//                                        .productId(i.getProductId())
//                                        .quantity(i.getQuantity())
//                                        .price(i.getPrice())
//                                        .subtotal(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
//                                        .build()))
//                                .collectList()
//                                .map(savedItems -> {
//                                    List<com.zivdah.common.dto.OrderItemDto> eventItems = savedItems.stream()
//                                            .map(si -> com.zivdah.common.dto.OrderItemDto.builder()
//                                                    .productId(si.getProductId())
//                                                    .quantity(si.getQuantity())
//                                                    .price(si.getPrice())
//                                                    .build())
//                                            .collect(Collectors.toList());
//                                    orderKafkaProducer.publishOrderCreated(
//                                            OrderCreatedEvent.builder()
//                                                    .orderId(savedOrder.getId())
//                                                    .userId(savedOrder.getUserId())
//                                                    .totalAmount(savedOrder.getTotalAmount())
//                                                    .items(eventItems)
//                                                    .build());
//                                    log.info("Order {} created, event published", savedOrder.getId());
//                                    return mapToResponse(savedOrder, savedItems);
//                                })
//                );
//    }
//
//    @Override
//    public Mono<OrderResponseDto> getOrderById(Long orderId) {
//        return orderRepository.findById(orderId)
//                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId)))
//                .flatMap(order -> orderItemRepository.findByOrderId(order.getId())
//                        .collectList()
//                        .map(items -> mapToResponse(order, items)));
//    }
//
//    @Override
//    public Flux<OrderResponseDto> getOrdersByUser(Long userId) {
//        return orderRepository.findByUserId(userId)
//                .flatMap(order -> orderItemRepository.findByOrderId(order.getId())
//                        .collectList()
//                        .map(items -> mapToResponse(order, items)));
//    }
//
//    @Override
//    public Mono<Void> cancelOrder(Long orderId) {
//        return orderRepository.findById(orderId)
//                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId)))
//                .flatMap(order -> {
//                    order.setStatus(OrderStatus.CANCELLED);
//                    return orderRepository.save(order);
//                })
//                .then();
//    }
//
//    private OrderResponseDto mapToResponse(Order order, List<OrderItem> items) {
//        return OrderResponseDto.builder()
//                .orderId(order.getId()).userId(order.getUserId())
//                .totalAmount(order.getTotalAmount()).status(order.getStatus())
//                .createdAt(order.getCreatedAt())
//                .deliveryAddressLine1(order.getDeliveryAddressLine1())
//                .deliveryAddressLine2(order.getDeliveryAddressLine2())
//                .deliveryCity(order.getDeliveryCity())
//                .deliveryState(order.getDeliveryState())
//                .deliveryPinCode(order.getDeliveryPinCode())
//                .items(items.stream().map(i -> OrderItemDto.builder()
//                        .productId(i.getProductId())
//                        .quantity(i.getQuantity())
//                        .price(i.getPrice())
//                        .build()).collect(Collectors.toList()))
//                .build();
//    }
//}
