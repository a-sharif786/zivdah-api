package com.zivdah.order.serviceImpl;

import com.zivdah.common.event.OrderCreatedEvent;
import com.zivdah.common.event.OrderStatusChangedEvent;
import com.zivdah.order.client.PaymentServiceClient;
import com.zivdah.order.dto.OrderItemDto;
import com.zivdah.order.dto.OrderRequestDto;
import com.zivdah.order.entity.Order;
import com.zivdah.order.entity.OrderItem;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.kafka.OrderKafkaProducer;
import com.zivdah.order.repository.OrderItemRepository;
import com.zivdah.order.repository.OrderRepository;
import com.zivdah.order.service.InvoiceService;
import com.zivdah.order.service.OrderPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Regression + abuse coverage for the order lifecycle after the P0 payment-integrity fixes:
// normal checkout, successful/failed payment, cancellation, refund and admin/vendor status flows.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    private static final long CUSTOMER = 7L;
    private static final long OTHER_USER = 8L;
    private static final long VENDOR = 50L;
    private static final long ADMIN = 1L;

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderKafkaProducer orderKafkaProducer;
    @Mock private PaymentServiceClient paymentServiceClient;
    @Mock private InvoiceService invoiceService;
    @Mock private OrderPricingService orderPricingService;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, orderItemRepository, orderKafkaProducer,
                paymentServiceClient, invoiceService, orderPricingService);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(100L);
            return Mono.just(o);
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(invoiceService.generateInvoice(anyLong(), any(), any(), any())).thenReturn(Mono.empty());
        when(paymentServiceClient.refundOrderPayment(anyLong())).thenReturn(Mono.empty());
    }

    private Order order(OrderStatus status) {
        return Order.builder().id(100L).userId(CUSTOMER).status(status)
                .totalAmount(new BigDecimal("302.50")).createdAt(LocalDateTime.now()).build();
    }

    private void stored(Order order) {
        when(orderRepository.findById(order.getId())).thenReturn(Mono.just(order));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(Flux.just(
                OrderItem.builder().orderId(order.getId()).productId(1L).vendorId(VENDOR)
                        .quantity(1).price(new BigDecimal("100")).subtotal(new BigDecimal("100")).build()));
    }

    // --- checkout ------------------------------------------------------------------------

    @Nested
    class Checkout {

        private OrderRequestDto request(String clientTotal) {
            return OrderRequestDto.builder()
                    .idempotencyKey("chk-1").userId(OTHER_USER)          // body claims another user
                    .subTotal(new BigDecimal("1")).totalAmount(new BigDecimal(clientTotal))
                    .deliveryAddressLine1("1 Main St").deliveryCity("Pune").deliveryState("MH").deliveryPinCode("411001")
                    .currency("INR")
                    .items(List.of(OrderItemDto.builder().productId(1L).quantity(2)
                            .price(new BigDecimal("1")).vendorId(999L).build()))
                    .build();
        }

        private OrderPricingService.PricedOrder priced() {
            return OrderPricingService.PricedOrder.builder()
                    .lines(List.of(OrderPricingService.PricedLine.builder().productId(1L).vendorId(VENDOR).quantity(2)
                            .unitPrice(new BigDecimal("125.00")).subtotal(new BigDecimal("250.00")).build()))
                    .subTotal(new BigDecimal("250.00")).taxAmount(new BigDecimal("12.50"))
                    .deliveryCharge(new BigDecimal("40.00")).packagingCharge(BigDecimal.ZERO.setScale(2))
                    .handlingCharge(BigDecimal.ZERO.setScale(2)).discountAmount(BigDecimal.ZERO.setScale(2))
                    .totalAmount(new BigDecimal("302.50")).build();
        }

        @Test
        void normalCheckoutStoresServerPricesForTheAuthenticatedUser() {
            when(orderRepository.findByIdempotencyKey("chk-1")).thenReturn(Mono.empty());
            when(orderPricingService.price(anyList(), any())).thenReturn(Mono.just(priced()));

            StepVerifier.create(orderService.createOrder(request("302.50"), CUSTOMER))
                    .assertNext(r -> {
                        assertThat(r.getUserId()).isEqualTo(CUSTOMER);               // not the body's 8
                        assertThat(r.getTotalAmount()).isEqualByComparingTo("302.50");
                        assertThat(r.getSubTotal()).isEqualByComparingTo("250.00");  // not the body's 1
                        assertThat(r.getStatus()).isEqualTo(OrderStatus.CREATED);
                        assertThat(r.getItems()).singleElement().satisfies(i -> {
                            assertThat(i.getPrice()).isEqualByComparingTo("125.00");
                            assertThat(i.getVendorId()).isEqualTo(VENDOR);          // not the body's 999
                        });
                    })
                    .verifyComplete();
            ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(orderKafkaProducer).publishOrderCreated(event.capture());
            assertThat(event.getValue().getTotalAmount()).isEqualByComparingTo("302.50");
        }

        @Test
        void oneRupeeTamperedTotalIsRejectedAndNothingIsSaved() {
            when(orderRepository.findByIdempotencyKey("chk-1")).thenReturn(Mono.empty());
            when(orderPricingService.price(anyList(), any())).thenReturn(Mono.just(priced()));

            StepVerifier.create(orderService.createOrder(request("1.00"), CUSTOMER))
                    .expectErrorSatisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT))
                    .verify();
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(orderKafkaProducer);
        }

        @Test
        void onePaiseFloatingPointDifferenceIsTolerated() {
            when(orderRepository.findByIdempotencyKey("chk-1")).thenReturn(Mono.empty());
            when(orderPricingService.price(anyList(), any())).thenReturn(Mono.just(priced()));
            StepVerifier.create(orderService.createOrder(request("302.51"), CUSTOMER))
                    .assertNext(r -> assertThat(r.getTotalAmount()).isEqualByComparingTo("302.50"))
                    .verifyComplete();
        }

        @Test
        void retryWithSameKeyReturnsExistingOrderWithoutRepricing() {
            Order existing = order(OrderStatus.CREATED);
            when(orderRepository.findByIdempotencyKey("chk-1")).thenReturn(Mono.just(existing));
            when(orderItemRepository.findByOrderId(100L)).thenReturn(Flux.empty());
            StepVerifier.create(orderService.createOrder(request("302.50"), CUSTOMER))
                    .assertNext(r -> assertThat(r.getOrderId()).isEqualTo(100L))
                    .verifyComplete();
            verifyNoInteractions(orderPricingService);
        }

        @Test
        void someoneElsesIdempotencyKeyIsRejected() {
            when(orderRepository.findByIdempotencyKey("chk-1")).thenReturn(Mono.just(order(OrderStatus.CREATED)));
            StepVerifier.create(orderService.createOrder(request("302.50"), OTHER_USER))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }
    }

    // --- payment outcome (internal payment-service sync) ------------------------------------

    @Nested
    class PaymentOutcome {

        @Test
        void successfulPaymentMarksCreatedOrderPaidAndGeneratesInvoice() {
            Order o = order(OrderStatus.CREATED);
            stored(o);
            LocalDateTime paidAt = LocalDateTime.now();
            StepVerifier.create(orderService.updatePaymentStatus(100L, OrderStatus.PAID, "UPI", "txn-1", paidAt))
                    .verifyComplete();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(invoiceService).generateInvoice(100L, "UPI", "txn-1", paidAt);
        }

        @Test
        void failedPaymentCancelsUnpaidOrder() {
            Order o = order(OrderStatus.CREATED);
            stored(o);
            StepVerifier.create(orderService.updatePaymentStatus(100L, OrderStatus.CANCELLED, null, null, null))
                    .verifyComplete();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(invoiceService, never()).generateInvoice(anyLong(), any(), any(), any());
        }

        @Test
        void retriedPaymentAfterFailureSettlesTheSameCancelledOrder() {
            // storefront "retry payment" reuses the order the failed attempt cancelled
            Order o = order(OrderStatus.CANCELLED);
            stored(o);
            StepVerifier.create(orderService.updatePaymentStatus(100L, OrderStatus.PAID, "UPI", "txn-2", LocalDateTime.now()))
                    .verifyComplete();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "PACKING", "OUT_FOR_DELIVERY", "DELIVERED", "REFUNDED"})
        void paidCannotRewindAnOrderThatIsAlreadyPaidOrFinished(OrderStatus current) {
            Order o = order(current);
            stored(o);
            StepVerifier.create(orderService.updatePaymentStatus(100L, OrderStatus.PAID, "UPI", "t", LocalDateTime.now()))
                    .expectError(ResponseStatusException.class)
                    .verify();
            assertThat(o.getStatus()).isEqualTo(current);
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PAID", "CONFIRMED", "DELIVERED"})
        void failedPaymentCannotCancelAPaidOrder(OrderStatus current) {
            Order o = order(current);
            stored(o);
            StepVerifier.create(orderService.updatePaymentStatus(100L, OrderStatus.CANCELLED, null, null, null))
                    .expectError(ResponseStatusException.class)
                    .verify();
            assertThat(o.getStatus()).isEqualTo(current);
        }

        @Test
        void samePaidTwiceIsIdempotent() {
            Order o = order(OrderStatus.PAID);
            stored(o);
            StepVerifier.create(orderService.updatePaymentStatus(100L, OrderStatus.PAID, "UPI", "t", LocalDateTime.now()))
                    .verifyComplete();
            verify(orderRepository, never()).save(any());
        }

        @Test
        void fullRefundSyncMarksDeliveredOrderRefunded() {
            Order o = order(OrderStatus.DELIVERED);
            stored(o);
            StepVerifier.create(orderService.updatePaymentStatus(100L, OrderStatus.REFUNDED, null, null, null))
                    .verifyComplete();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            verify(orderKafkaProducer).publishOrderStatusChanged(any(OrderStatusChangedEvent.class));
        }

        @Test
        void deliveryUpdateNeverRevivesACancelledOrder() {
            Order o = order(OrderStatus.CANCELLED);
            stored(o);
            StepVerifier.create(orderService.syncDeliveryStatus(100L, "DELIVERED")).verifyComplete();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void deliveryUpdateStillAdvancesAnActiveOrder() {
            Order o = order(OrderStatus.READY_FOR_DELIVERY);
            stored(o);
            StepVerifier.create(orderService.syncDeliveryStatus(100L, "ON_THE_WAY")).verifyComplete();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        }
    }

    // --- cancellation ------------------------------------------------------------------------

    @Nested
    class Cancellation {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"CREATED", "PAYMENT_PENDING", "PAID", "CONFIRMED"})
        void customerCancelsOwnOrderInCancellableStatus(OrderStatus current) {
            Order o = order(current);
            stored(o);
            StepVerifier.create(orderService.cancelOrder(100L, CUSTOMER, "CUSTOMER")).verifyComplete();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderKafkaProducer).publishOrderStatusChanged(any(OrderStatusChangedEvent.class));
        }

        @Test
        void customerCannotCancelSomeoneElsesOrder() {
            Order o = order(OrderStatus.CREATED);
            stored(o);
            StepVerifier.create(orderService.cancelOrder(100L, OTHER_USER, "CUSTOMER"))
                    .expectErrorSatisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN))
                    .verify();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.CREATED);
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PACKING", "OUT_FOR_DELIVERY", "DELIVERED", "REFUNDED"})
        void customerCannotCancelOnceFulfilmentStarted(OrderStatus current) {
            Order o = order(current);
            stored(o);
            StepVerifier.create(orderService.cancelOrder(100L, CUSTOMER, "CUSTOMER"))
                    .expectError(ResponseStatusException.class)
                    .verify();
            assertThat(o.getStatus()).isEqualTo(current);
        }

        @Test
        void adminCanStillCancelAnOrderOutForDelivery() {
            Order o = order(OrderStatus.OUT_FOR_DELIVERY);
            stored(o);
            StepVerifier.create(orderService.cancelOrder(100L, ADMIN, "ADMIN")).verifyComplete();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void adminCannotCancelADeliveredOrder() {
            Order o = order(OrderStatus.DELIVERED);
            stored(o);
            StepVerifier.create(orderService.cancelOrder(100L, ADMIN, "ADMIN"))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }
    }

    // --- admin/vendor status flow -----------------------------------------------------------

    @Nested
    class StatusFlow {

        @Test
        void vendorCanNoLongerMarkAnOrderPaid() {
            Order o = order(OrderStatus.CREATED);
            stored(o);
            StepVerifier.create(orderService.updateStatus(100L, OrderStatus.PAID, VENDOR, "VENDOR"))
                    .expectErrorSatisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN))
                    .verify();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.CREATED);
        }

        @Test
        void adminKeepsManualPaidOverride() {
            Order o = order(OrderStatus.CREATED);
            stored(o);
            StepVerifier.create(orderService.updateStatus(100L, OrderStatus.PAID, ADMIN, "ADMIN"))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(OrderStatus.PAID))
                    .verifyComplete();
        }

        @Test
        void vendorFulfilmentFlowStillWorks() {
            Order o = order(OrderStatus.PAID);
            stored(o);
            StepVerifier.create(orderService.updateStatus(100L, OrderStatus.CONFIRMED, VENDOR, "VENDOR"))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(OrderStatus.CONFIRMED))
                    .verifyComplete();
        }

        @Test
        void vendorWithoutItemsOnOrderIsRejected() {
            Order o = order(OrderStatus.PAID);
            stored(o);
            StepVerifier.create(orderService.updateStatus(100L, OrderStatus.CONFIRMED, 51L, "VENDOR"))
                    .expectError(ResponseStatusException.class)
                    .verify();
        }

        @Test
        void adminRefundAlsoRefundsThePayment() {
            Order o = order(OrderStatus.DELIVERED);
            stored(o);
            StepVerifier.create(orderService.updateStatus(100L, OrderStatus.REFUNDED, ADMIN, "ADMIN"))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(OrderStatus.REFUNDED))
                    .verifyComplete();
            verify(paymentServiceClient).refundOrderPayment(100L);
        }

        @Test
        void vendorCannotRefund() {
            StepVerifier.create(orderService.updateStatus(100L, OrderStatus.REFUNDED, VENDOR, "VENDOR"))
                    .expectError(ResponseStatusException.class)
                    .verify();
            verify(paymentServiceClient, never()).refundOrderPayment(eq(100L));
        }
    }
}
