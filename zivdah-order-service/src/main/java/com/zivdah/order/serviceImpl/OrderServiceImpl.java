package com.zivdah.order.serviceImpl;

import com.zivdah.common.event.OrderCreatedEvent;
import com.zivdah.common.event.OrderStatusChangedEvent;
import com.zivdah.order.client.PaymentServiceClient;
import com.zivdah.order.dto.OrderItemDto;
import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.dto.OrderResponseDto;
import com.zivdah.order.dto.OrderStatsResponseDto;
import com.zivdah.order.entity.Order;
import com.zivdah.order.entity.OrderItem;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.kafka.OrderKafkaProducer;
import com.zivdah.order.repository.OrderItemRepository;
import com.zivdah.order.repository.OrderRepository;
import com.zivdah.order.service.OrderPricingService;
import com.zivdah.order.service.OrderService;
import com.zivdah.order.service.InvoiceService;
import com.zivdah.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderKafkaProducer orderKafkaProducer;
    private final PaymentServiceClient paymentServiceClient;
    private final InvoiceService invoiceService;
    private final OrderPricingService orderPricingService;

    // Client's expected total may differ from the server's by at most one paisa — the storefront
    // computes tax with JS floating-point toFixed(2), which can round the last digit differently
    // from BigDecimal HALF_UP. Anything larger is a real price difference.
    static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.01");

    // Order statuses a failed payment may still cancel — the order hasn't been paid yet.
    static final Set<OrderStatus> AWAITING_PAYMENT = EnumSet.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING);

    // Order statuses a confirmed payment may move to PAID — AWAITING_PAYMENT plus CANCELLED (see
    // updatePaymentStatus for why: a failed-then-retried UPI payment settles the same order).
    static final Set<OrderStatus> PAYABLE =
            EnumSet.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED);

    // Already-finished orders even an ADMIN can't cancel (a delivered order is refunded instead).
    private static final Set<OrderStatus> ADMIN_NON_CANCELLABLE =
            EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    // Allowed forward transitions for the admin/vendor-driven lifecycle. Anything not
    // listed here (e.g. skipping straight from CREATED to DELIVERED) is rejected.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.CREATED, EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.PAID, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAYMENT_PENDING, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAID, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PACKING, OrderStatus.CANCELLED, OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PACKING, EnumSet.of(OrderStatus.READY_FOR_DELIVERY, OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.READY_FOR_DELIVERY, EnumSet.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.OUT_FOR_DELIVERY, EnumSet.of(OrderStatus.DELIVERED, OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.of(OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));
    }


    // Order + its OrderItems must land together — a failed item save (e.g. a bad
    // vendorId/product row) must not leave an item-less Order behind for the delivery/vendor
    // flows to trip over later. Spring Boot auto-configures a ReactiveTransactionManager for
    // the single R2DBC ConnectionFactory this service already has, so this rolls the whole
    // save (order + all items) back on any error in the chain below.
    @Override
    @Transactional
    public Mono<OrderResponseDto> createOrder(OrderRequestDto dto, Long currentUserId) {
        if (isBlank(dto.getIdempotencyKey())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "idempotencyKey is required"));
        }
        // The order always belongs to the authenticated caller — the request body's userId is
        // ignored (it used to be trusted, letting anyone place an order on another user's account).
        dto.setUserId(currentUserId);

        // idempotencyKey identifies one checkout attempt end-to-end — looking it up first makes
        // retrying "Place Order" for the same cart idempotent: an existing order is returned
        // as-is (no re-insert, no re-publish of the order-created Kafka event, which is what
        // would otherwise double-decrement inventory stock and double-notify everyone). See
        // PaymentServiceImpl#initiatePayment for the matching payment-side check.
        return orderRepository.findByIdempotencyKey(dto.getIdempotencyKey())
                .flatMap(existing -> {
                    // Never hand back someone else's order just because the key collided.
                    if (!currentUserId.equals(existing.getUserId())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate checkout reference"));
                    }
                    return orderItemRepository.findByOrderId(existing.getId())
                            .collectList()
                            .map(items -> mapToResponse(existing, items));
                })
                .switchIfEmpty(Mono.defer(() -> insertNewOrder(dto)));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // Light sanity validation before pricing — items/address must be present. Prices are NOT
    // validated here any more: they're recomputed from scratch by OrderPricingService. Deliberately
    // NOT checking inventory/stock availability here: that's only ever done asynchronously today
    // via the order-created Kafka consumer, with no rollback path if insufficient — a separate,
    // pre-existing architectural gap, not something to half-fix as part of this change.
    private ResponseStatusException validateNewOrder(OrderRequestDto dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain at least one item");
        }
        for (OrderItemDto item : dto.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each item quantity must be positive");
            }
        }
        if (isBlank(dto.getDeliveryAddressLine1()) || isBlank(dto.getDeliveryCity())
                || isBlank(dto.getDeliveryState()) || isBlank(dto.getDeliveryPinCode())) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery address is incomplete");
        }
        if (dto.getTotalAmount() == null) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, "totalAmount is required");
        }
        return null;
    }

    private Mono<OrderResponseDto> insertNewOrder(OrderRequestDto dto) {
        ResponseStatusException validationError = validateNewOrder(dto);
        if (validationError != null) {
            return Mono.error(validationError);
        }
        return orderPricingService.price(dto.getItems(), dto.getCouponCode())
                .flatMap(priced -> {
                    // The client's total is only ever an "expected total" now: every stored number
                    // below is the server's own. A mismatch means the cart is stale (a price or
                    // coupon changed since the customer loaded it) or the request was tampered with
                    // — either way, don't create an order the customer didn't see the price of.
                    if (dto.getTotalAmount().subtract(priced.getTotalAmount()).abs().compareTo(TOTAL_TOLERANCE) > 0) {
                        log.warn("Order total mismatch for user {}: client {} vs server {}",
                                dto.getUserId(), dto.getTotalAmount(), priced.getTotalAmount());
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Prices in your cart have changed. Please review your cart and try again."));
                    }
                    return insertPricedOrder(dto, priced);
                });
    }

    private Mono<OrderResponseDto> insertPricedOrder(OrderRequestDto dto, OrderPricingService.PricedOrder priced) {
        log.info("Creating order for user {} with {} item(s)", dto.getUserId(), priced.getLines().size());

        Order order = Order.builder()
                .idempotencyKey(dto.getIdempotencyKey())
                .userId(dto.getUserId())

                .orderNumber(generateOrderNumber())   // <-- Add this

                .subTotal(priced.getSubTotal())

                // Same tax split the storefront has always sent: the whole tax as GST, no
                // CGST/SGST/IGST breakdown.
                .gstAmount(priced.getTaxAmount())
                .cgstAmount(BigDecimal.ZERO)
                .sgstAmount(BigDecimal.ZERO)
                .igstAmount(BigDecimal.ZERO)
                .totalTaxAmount(priced.getTaxAmount())

                .deliveryCharge(priced.getDeliveryCharge())
                .packagingCharge(priced.getPackagingCharge())
                .handlingCharge(priced.getHandlingCharge())

                .discountAmount(priced.getDiscountAmount())
                .couponCode(priced.getCouponCode())

                .totalAmount(priced.getTotalAmount())

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

                        Flux.fromIterable(priced.getLines())

                                // price/vendorId come from product-service (OrderPricingService),
                                // never from the request's own item fields.
                                .flatMap(line ->
                                        orderItemRepository.save(
                                                OrderItem.builder()
                                                        .orderId(savedOrder.getId())
                                                        .productId(line.getProductId())
                                                        .vendorId(line.getVendorId())
                                                        .quantity(line.getQuantity())
                                                        .price(line.getUnitPrice())
                                                        .subtotal(line.getSubtotal())
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
    public Mono<Void> cancelOrder(Long orderId, Long currentUserId, String role) {

        return orderRepository.findById(orderId)

                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Order not found"
                                )
                        )
                )

                .flatMap(order -> orderItemRepository.findByOrderId(orderId).collectList()
                        .flatMap(items -> {
                            // Previously any authenticated user could cancel any order, in any
                            // status (even DELIVERED/REFUNDED). Now: ADMIN may cancel anything not
                            // already finished (what zivdah-admin's OrderDetailPage offers); the
                            // customer — or a VENDOR with an item on it — only while the order still
                            // allows CANCELLED per ALLOWED_TRANSITIONS (what zivdah-web's
                            // OrderDetail CANCELLABLE_STATUSES offers).
                            boolean admin = "ADMIN".equalsIgnoreCase(role);
                            boolean owner = currentUserId != null && currentUserId.equals(order.getUserId());
                            boolean vendorOnOrder = "VENDOR".equalsIgnoreCase(role)
                                    && items.stream().anyMatch(i -> currentUserId.equals(i.getVendorId()));
                            if (!admin && !owner && !vendorOnOrder) {
                                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this order"));
                            }
                            boolean cancellable = admin
                                    ? !ADMIN_NON_CANCELLABLE.contains(order.getStatus())
                                    : ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), EnumSet.noneOf(OrderStatus.class))
                                            .contains(OrderStatus.CANCELLED);
                            if (!cancellable) {
                                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Order in status " + order.getStatus() + " can no longer be cancelled"));
                            }
                            return Mono.just(order);
                        }))
                .flatMap(order -> {

                    OrderStatus oldStatus = order.getStatus();
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setUpdatedAt(LocalDateTime.now());

                    return orderRepository.save(order)
                            .doOnSuccess(saved -> orderKafkaProducer.publishOrderStatusChanged(
                                    OrderStatusChangedEvent.builder()
                                            .orderId(saved.getId())
                                            .userId(saved.getUserId())
                                            .oldStatus(oldStatus.name())
                                            .newStatus(OrderStatus.CANCELLED.name())
                                            .changedByUserId(currentUserId)
                                            .changedByRole(role)
                                            .build()));
                })

                .then();
    }



    @Override
    public Mono<OrderResponseDto> updateStatus(Long orderId, OrderStatus newStatus, Long currentUserId, String role) {

        // Financial action, and there's no real payment-gateway refund integration yet — ADMIN
        // only, checked up front before touching anything.
        if (newStatus == OrderStatus.REFUNDED && !"ADMIN".equalsIgnoreCase(role)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only ADMIN may mark an order as refunded"));
        }
        // PAID is a payment outcome, not a fulfilment step. A VENDOR could previously set it on
        // any order containing one of their items — and since anyone can self-register as a
        // VENDOR, that let a customer mark their own unpaid order PAID. Payments now move orders
        // to PAID only via payment-service (gateway-confirmed UPI, or an admin marking COD
        // collected); ADMIN keeps the manual override it already had.
        if (newStatus == OrderStatus.PAID && !"ADMIN".equalsIgnoreCase(role)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only a confirmed payment (or an ADMIN) may mark an order as paid"));
        }

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
                        orderItemRepository.findByOrderId(orderId).collectList()
                                .flatMap(items -> {
                                    // A VENDOR may only transition an order that contains at
                                    // least one of their own items (an order can span multiple
                                    // vendors — see OrderItem.vendorId).
                                    if ("VENDOR".equalsIgnoreCase(role)
                                            && items.stream().noneMatch(i -> currentUserId.equals(i.getVendorId()))) {
                                        return Mono.<List<OrderItem>>error(new ResponseStatusException(
                                                HttpStatus.FORBIDDEN, "Not the owner of this order"));
                                    }
                                    return Mono.just(items);
                                })
                                .flatMap(items -> {
                                    Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), EnumSet.noneOf(OrderStatus.class));
                                    if (!allowed.contains(newStatus)) {
                                        return Mono.error(new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Cannot transition order from " + order.getStatus() + " to " + newStatus
                                        ));
                                    }

                                    OrderStatus oldStatus = order.getStatus();
                                    order.setStatus(newStatus);
                                    order.setUpdatedAt(LocalDateTime.now());

                                    return orderRepository.save(order)
                                            .doOnSuccess(saved -> {
                                                orderKafkaProducer.publishOrderStatusChanged(
                                                        OrderStatusChangedEvent.builder()
                                                                .orderId(saved.getId())
                                                                .userId(saved.getUserId())
                                                                .oldStatus(oldStatus.name())
                                                                .newStatus(newStatus.name())
                                                                .changedByUserId(currentUserId)
                                                                .changedByRole(role)
                                                                .build());
                                                // Best-effort: also refund the underlying payment so the
                                                // dashboard's "Payment Received" figure reflects this refund
                                                // immediately, not just the order's own status.
                                                if (newStatus == OrderStatus.REFUNDED) {
                                                    paymentServiceClient.refundOrderPayment(saved.getId()).subscribe();
                                                }
                                            })
                                            .map(saved -> mapToResponse(saved, items));
                                })
                );
    }



    @Override
    public Mono<Void> updatePaymentStatus(
            Long orderId, OrderStatus newStatus, String paymentMethod, String transactionId, LocalDateTime paidAt) {

        if (newStatus != OrderStatus.PAID && newStatus != OrderStatus.CANCELLED && newStatus != OrderStatus.REFUNDED) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "payment-status can only be set to PAID, CANCELLED or REFUNDED"
            ));
        }

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

                    // Idempotent: this sync call can race with (or follow) the admin-driven
                    // updateStatus() path already having set the same status.
                    if (order.getStatus() == newStatus) {
                        return Mono.<Order>empty();
                    }

                    // A payment result only applies to an order that isn't paid/fulfilled yet.
                    // Previously PAID was accepted from ANY status (knocking a delivered order back
                    // to PAID, re-paying a REFUNDED one) and a failed payment could cancel an order
                    // already in fulfilment. PAID is still accepted on a CANCELLED order: a failed
                    // UPI attempt cancels its order, and the storefront's "retry payment" reuses that
                    // same order (same checkoutRef), so a successful retry must be able to settle it.
                    // That's safe now that only a gateway-verified payment can reach this endpoint.
                    boolean payable = newStatus == OrderStatus.PAID
                            ? PAYABLE.contains(order.getStatus())
                            : AWAITING_PAYMENT.contains(order.getStatus());
                    if ((newStatus == OrderStatus.PAID || newStatus == OrderStatus.CANCELLED) && !payable) {
                        log.error("Rejected payment-status {} for order {} in status {}",
                                newStatus, orderId, order.getStatus());
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Order " + orderId + " is not awaiting payment (status " + order.getStatus() + ")"));
                    }

                    if (newStatus == OrderStatus.REFUNDED) {
                        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), EnumSet.noneOf(OrderStatus.class));
                        if (!allowed.contains(OrderStatus.REFUNDED)) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST, "Cannot refund an order in status " + order.getStatus()));
                        }
                    }

                    OrderStatus oldStatus = order.getStatus();
                    order.setStatus(newStatus);
                    order.setUpdatedAt(LocalDateTime.now());

                    return orderRepository.save(order)
                            .doOnSuccess(saved -> {
                                // Only for REFUNDED — PAID/CANCELLED via this internal path stay silent,
                                // as before. This is what makes notification-service's existing
                                // "Refund Completed" handling fire for this sync path too.
                                if (newStatus == OrderStatus.REFUNDED) {
                                    orderKafkaProducer.publishOrderStatusChanged(
                                            OrderStatusChangedEvent.builder()
                                                    .orderId(saved.getId())
                                                    .userId(saved.getUserId())
                                                    .oldStatus(oldStatus.name())
                                                    .newStatus(newStatus.name())
                                                    .changedByRole("SYSTEM")
                                                    .build());
                                }
                                // Invoice Management flow: ORDER_CREATED -> PAYMENT_SUCCESS ->
                                // GENERATE_INVOICE -> INVOICE_GENERATED. Best-effort and
                                // fire-and-forget, same reasoning as refundOrderPayment() above —
                                // a PDF-generation/storage hiccup must not fail the payment-status
                                // sync itself (the payment already succeeded); it can be retried via
                                // POST /invoices/generate/{orderId} later.
                                if (newStatus == OrderStatus.PAID) {
                                    invoiceService.generateInvoice(saved.getId(), paymentMethod, transactionId, paidAt)
                                            .doOnError(ex -> log.error(
                                                    "Invoice generation failed for order {}: {}", saved.getId(), ex.getMessage()))
                                            .onErrorResume(ex -> Mono.empty())
                                            .subscribe();
                                }
                            });
                })

                .then();
    }

    @Override
    public Mono<Void> syncDeliveryStatus(Long orderId, String deliveryStatus) {
        OrderStatus mapped = switch (deliveryStatus == null ? "" : deliveryStatus) {
            case "ON_THE_WAY" -> OrderStatus.OUT_FOR_DELIVERY;
            case "DELIVERED" -> OrderStatus.DELIVERED;
            default -> null;
        };
        if (mapped == null) {
            return Mono.empty();
        }
        return orderRepository.findById(orderId)
                // A cancelled/refunded order must never be revived by a delivery update.
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.REFUNDED)
                .flatMap(order -> {
                    order.setStatus(mapped);
                    order.setUpdatedAt(LocalDateTime.now());
                    return orderRepository.save(order);
                })
                .then();
    }



    @Override
    public Flux<OrderResponseDto> getAllOrders(Pageable pageable, OrderStatus status) {
        Flux<Order> orders = status != null
                ? orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : orderRepository.findAllByOrderByCreatedAtDesc(pageable);

        return orders.flatMap(order ->
                orderItemRepository.findByOrderId(order.getId())
                        .collectList()
                        .map(items -> mapToResponse(order, items))
        );
    }

    @Override
    public Mono<OrderStatsResponseDto> getStats(LocalDateTime from, LocalDateTime to) {

        Mono<Long> totalOrders = orderRepository.count();

        Mono<List<Order>> ordersInRange = orderRepository.findByCreatedAtBetween(from, to).collectList();

        return Mono.zip(totalOrders, ordersInRange)
                .map(t -> {
                    List<Order> orders = t.getT2();

                    Map<OrderStatus, Long> statusBreakdown = orders.stream()
                            .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

                    BigDecimal revenueInRange = orders.stream()
                            .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                            .map(Order::getTotalAmount)
                            .filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return OrderStatsResponseDto.builder()
                            .totalOrders(t.getT1())
                            .ordersInRange(orders.size())
                            .statusBreakdown(statusBreakdown)
                            .revenueInRange(revenueInRange)
                            .build();
                });
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

                    // findAllById(...) does not preserve the input order or guarantee any
                    // particular one, so sort the merged result explicitly — newest order first.
                    return orderRepository.findAllById(orderIds)
                            .map(order -> mapToResponse(order, itemsByOrder.get(order.getId())))
                            .collectList()
                            .flatMapMany(list -> {
                                list.sort(Comparator.comparing(OrderResponseDto::getCreatedAt).reversed());
                                return Flux.fromIterable(list);
                            });
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
