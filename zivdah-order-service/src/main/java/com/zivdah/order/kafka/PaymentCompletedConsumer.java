package com.zivdah.order.kafka;

import com.zivdah.common.event.PaymentCompletedEvent;
import com.zivdah.order.client.PaymentServiceClient;
import com.zivdah.order.client.dto.PaymentSummaryDto;
import com.zivdah.order.entity.Order;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// Best-effort backstop for payment-service's synchronous PUT /orders/{id}/payment-status call
// (which is the primary path and usually lands first — in which case this is a no-op).
//
// A Kafka message is NOT trusted on its own: the broker has no authentication, so anyone who can
// reach it can publish a "payment-completed" event. The event is treated only as a hint to go and
// check payment-service's own record of the order's payments (over the authenticated internal
// API), and the order is only changed if that record agrees — PAID needs a SUCCESS payment whose
// amount covers the order total; CANCELLED needs a FAILED payment and no successful one. Same
// status rules as OrderServiceImpl#updatePaymentStatus.
//
// Kafka listeners run on Kafka's blocking thread pool — .block() is safe here
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentCompletedConsumer {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final Set<OrderStatus> PAYABLE =
            EnumSet.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED);
    private static final Set<OrderStatus> AWAITING_PAYMENT =
            EnumSet.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING);

    private final OrderRepository orderRepository;
    private final PaymentServiceClient paymentServiceClient;

    @KafkaListener(topics = "payment-completed", groupId = "order-group")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Payment {} for order {}", event.getStatus(), event.getOrderId());
        if (event.getOrderId() == null) {
            return;
        }

        Order order = orderRepository.findById(event.getOrderId()).block();
        if (order == null) {
            log.error("Order not found: {}", event.getOrderId());
            return;
        }

        boolean paidEvent = "PAID".equals(event.getStatus());
        OrderStatus target = paidEvent ? OrderStatus.PAID : OrderStatus.CANCELLED;
        if (order.getStatus() == target) {
            return; // the synchronous HTTP path already applied it
        }
        if (!(paidEvent ? PAYABLE : AWAITING_PAYMENT).contains(order.getStatus())) {
            log.warn("Ignoring payment-completed {} for order {} in status {}",
                    event.getStatus(), order.getId(), order.getStatus());
            return;
        }

        List<PaymentSummaryDto> payments = paymentServiceClient.getPaymentsForOrder(order.getId()).block();
        if (payments == null) {
            log.warn("Could not verify payment-completed {} for order {} with payment-service — ignoring",
                    event.getStatus(), order.getId());
            return;
        }
        boolean verified = paidEvent ? hasSuccessfulPaymentCovering(payments, order) : hasOnlyFailedPayments(payments);
        if (!verified) {
            log.error("payment-completed {} for order {} does not match payment-service's records — ignoring",
                    event.getStatus(), order.getId());
            return;
        }

        order.setStatus(target);
        orderRepository.save(order).block();
        log.info("Order {} status updated to {}", event.getOrderId(), order.getStatus());
    }

    private static boolean hasSuccessfulPaymentCovering(List<PaymentSummaryDto> payments, Order order) {
        return order.getTotalAmount() != null && payments.stream().anyMatch(p ->
                "SUCCESS".equals(p.getStatus()) && p.getAmount() != null
                        && p.getAmount().subtract(order.getTotalAmount()).abs().compareTo(AMOUNT_TOLERANCE) <= 0);
    }

    private static boolean hasOnlyFailedPayments(List<PaymentSummaryDto> payments) {
        return payments.stream().anyMatch(p -> "FAILED".equals(p.getStatus()))
                && payments.stream().noneMatch(p -> "SUCCESS".equals(p.getStatus()) || "REFUNDED".equals(p.getStatus()));
    }
}
