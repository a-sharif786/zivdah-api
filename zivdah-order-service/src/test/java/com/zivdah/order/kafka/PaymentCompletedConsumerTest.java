package com.zivdah.order.kafka;

import com.zivdah.common.event.PaymentCompletedEvent;
import com.zivdah.order.client.PaymentServiceClient;
import com.zivdah.order.client.dto.PaymentSummaryDto;
import com.zivdah.order.entity.Order;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentCompletedConsumerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentServiceClient paymentServiceClient;

    private PaymentCompletedConsumer consumer;
    private Order order;

    @BeforeEach
    void setUp() {
        consumer = new PaymentCompletedConsumer(orderRepository, paymentServiceClient);
        order = Order.builder().id(100L).userId(7L).status(OrderStatus.CREATED).totalAmount(new BigDecimal("302.50")).build();
        when(orderRepository.findById(100L)).thenReturn(Mono.just(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private static PaymentCompletedEvent event(String status) {
        return PaymentCompletedEvent.builder().orderId(100L).userId(7L).status(status).build();
    }

    private void payments(PaymentSummaryDto... list) {
        when(paymentServiceClient.getPaymentsForOrder(100L)).thenReturn(Mono.just(List.of(list)));
    }

    private static PaymentSummaryDto payment(String status, String amount) {
        return PaymentSummaryDto.builder().paymentId(1L).orderId(100L).status(status).amount(new BigDecimal(amount)).build();
    }

    @Test
    void verifiedPaidEventMarksOrderPaid() {
        payments(payment("SUCCESS", "302.50"));
        consumer.onPaymentCompleted(event("PAID"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void forgedPaidEventWithNoSuccessfulPaymentIsIgnored() {
        payments(payment("PROCESSING", "302.50"));
        consumer.onPaymentCompleted(event("PAID"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void paidEventForAnUnderpaidAmountIsIgnored() {
        payments(payment("SUCCESS", "1.00"));
        consumer.onPaymentCompleted(event("PAID"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void unverifiableEventIsIgnored() {
        when(paymentServiceClient.getPaymentsForOrder(100L)).thenReturn(Mono.empty());
        consumer.onPaymentCompleted(event("PAID"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void verifiedFailedEventCancelsOrder() {
        payments(payment("FAILED", "302.50"));
        consumer.onPaymentCompleted(event("FAILED"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void failedEventCannotCancelAnOrderWithASuccessfulPayment() {
        payments(payment("FAILED", "302.50"), payment("SUCCESS", "302.50"));
        consumer.onPaymentCompleted(event("FAILED"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void alreadyAppliedByHttpPathIsANoOp() {
        order.setStatus(OrderStatus.PAID);
        consumer.onPaymentCompleted(event("PAID"));
        verifyNoInteractions(paymentServiceClient);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void eventCannotRewindAnOrderInFulfilment() {
        order.setStatus(OrderStatus.DELIVERED);
        consumer.onPaymentCompleted(event("FAILED"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verifyNoInteractions(paymentServiceClient);
    }
}
