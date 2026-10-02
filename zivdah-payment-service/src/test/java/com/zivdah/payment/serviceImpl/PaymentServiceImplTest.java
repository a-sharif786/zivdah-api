package com.zivdah.payment.serviceImpl;

import com.zivdah.payment.client.OrderServiceClient;
import com.zivdah.payment.client.dto.OrderSnapshotDto;
import com.zivdah.payment.dto.PaymentRequestDto;
import com.zivdah.payment.entity.Payment;
import com.zivdah.payment.enums.PaymentMethod;
import com.zivdah.payment.enums.PaymentStatus;
import com.zivdah.payment.gateway.ecomworldpay.EcomWorldPayClient;
import com.zivdah.payment.gateway.ecomworldpay.TransactionNotYetAvailableException;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayTransactionDto;
import com.zivdah.payment.gateway.ecomworldpay.dto.QrIntentResponse;
import com.zivdah.payment.kafka.PaymentKafkaProducer;
import com.zivdah.payment.repository.PaymentRepository;
import com.zivdah.payment.repository.PaymentStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Regression + abuse coverage for the payment side of checkout after the P0 payment-integrity
// fixes: initiate / link / successful payment / failed payment / refund, plus forged callbacks,
// amount tampering and cross-user access.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    private static final long CUSTOMER = 7L;
    private static final long OTHER_USER = 8L;

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentStatsRepository paymentStatsRepository;
    @Mock private PaymentKafkaProducer paymentKafkaProducer;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private EcomWorldPayClient ecomWorldPayClient;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, paymentStatsRepository, paymentKafkaProducer,
                orderServiceClient, ecomWorldPayClient);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(11L);
            return Mono.just(p);
        });
        when(orderServiceClient.updatePaymentStatus(anyLong(), anyString())).thenReturn(Mono.empty());
        when(orderServiceClient.updatePaymentStatus(anyLong(), anyString(), any(), any(), any())).thenReturn(Mono.empty());
        when(ecomWorldPayClient.getMerchantId()).thenReturn("MID");
    }

    private static Payment processingUpi() {
        return Payment.builder().id(11L).userId(CUSTOMER).orderId(100L).amount(new BigDecimal("302.50"))
                .currency("INR").method(PaymentMethod.UPI).status(PaymentStatus.PROCESSING)
                .transactionId("inv-uuid-1").gatewayTxnId("PG123").checkoutRef("chk-1")
                .createdAt(LocalDateTime.now()).build();
    }

    private static EcomWorldPayTransactionDto gateway(String status, String amount, String invoice, String pgTxnId) {
        EcomWorldPayTransactionDto dto = new EcomWorldPayTransactionDto();
        dto.setStatus(status);
        dto.setAmount(amount == null ? null : new BigDecimal(amount));
        dto.setInvoiceNumber(invoice);
        dto.setPgTxnId(pgTxnId);
        dto.setRrn("RRN1");
        dto.setResponseMessage("SUCCESS".equals(status) ? "Approved" : "Declined by bank");
        return dto;
    }

    // --- initiate ----------------------------------------------------------------------------

    @Nested
    class Initiate {

        private PaymentRequestDto upiRequest(String amount) {
            return PaymentRequestDto.builder().checkoutRef("chk-1").userId(OTHER_USER).orderId(999L)
                    .amount(new BigDecimal(amount)).currency("INR").method(PaymentMethod.UPI)
                    .firstName("Asha").lastName("K").mobile("9800000001").email("a@x.com").build();
        }

        @Test
        void normalUpiCheckoutCreatesQrForTheAuthenticatedUserOnly() {
            when(paymentRepository.findByCheckoutRef("chk-1")).thenReturn(Mono.empty());
            QrIntentResponse qr = new QrIntentResponse();
            qr.setStatus("SUCCESS");
            qr.setTransactionId("PG123");
            qr.setIntent("upi://pay?pa=x");
            when(ecomWorldPayClient.createUpiIntent(any())).thenReturn(Mono.just(qr));

            StepVerifier.create(service.initiatePayment(upiRequest("302.50"), CUSTOMER))
                    .assertNext(r -> {
                        assertThat(r.getUserId()).isEqualTo(CUSTOMER);  // not the body's 8
                        assertThat(r.getOrderId()).isNull();             // body's 999 ignored — only linkOrder sets it
                        assertThat(r.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
                        assertThat(r.getUpiIntent()).isEqualTo("upi://pay?pa=x");
                    })
                    .verifyComplete();
        }

        @Test
        void codCheckoutCreatesPendingPayment() {
            when(paymentRepository.findByCheckoutRef("chk-1")).thenReturn(Mono.empty());
            PaymentRequestDto cod = PaymentRequestDto.builder().checkoutRef("chk-1")
                    .amount(new BigDecimal("302.50")).currency("INR").method(PaymentMethod.COD).build();
            StepVerifier.create(service.initiatePayment(cod, CUSTOMER))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(PaymentStatus.PENDING))
                    .verifyComplete();
            verify(ecomWorldPayClient, never()).createUpiIntent(any());
        }

        @Test
        void nonPositiveAmountIsRejected() {
            StepVerifier.create(service.initiatePayment(upiRequest("0"), CUSTOMER))
                    .expectError(ResponseStatusException.class).verify();
        }

        @Test
        void someoneElsesCheckoutRefIsRejected() {
            Payment existing = processingUpi();
            existing.setUserId(OTHER_USER);
            when(paymentRepository.findByCheckoutRef("chk-1")).thenReturn(Mono.just(existing));
            StepVerifier.create(service.initiatePayment(upiRequest("302.50"), CUSTOMER))
                    .expectError(ResponseStatusException.class).verify();
        }

        @Test
        void retryAfterFailureCannotRepriceAPaymentAlreadyLinkedToAnOrder() {
            Payment failed = processingUpi();
            failed.setStatus(PaymentStatus.FAILED);  // linked to order 100 at 302.50
            when(paymentRepository.findByCheckoutRef("chk-1")).thenReturn(Mono.just(failed));
            QrIntentResponse qr = new QrIntentResponse();
            qr.setStatus("SUCCESS");
            qr.setTransactionId("PG124");
            qr.setIntent("upi://pay?pa=y");
            when(ecomWorldPayClient.createUpiIntent(any())).thenReturn(Mono.just(qr));

            StepVerifier.create(service.initiatePayment(upiRequest("1.00"), CUSTOMER))
                    .assertNext(r -> {
                        assertThat(r.getAmount()).isEqualByComparingTo("302.50");
                        assertThat(r.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
                    })
                    .verifyComplete();
            verify(ecomWorldPayClient).createUpiIntent(argThat(req -> "302.50".equals(req.getAmount())));
        }

        @Test
        void retryOfUnlinkedFailedPaymentPicksUpTheNewCartAmount() {
            Payment failed = processingUpi();
            failed.setStatus(PaymentStatus.FAILED);
            failed.setOrderId(null);
            when(paymentRepository.findByCheckoutRef("chk-1")).thenReturn(Mono.just(failed));
            QrIntentResponse qr = new QrIntentResponse();
            qr.setStatus("SUCCESS");
            qr.setTransactionId("PG124");
            qr.setIntent("upi://pay?pa=y");
            when(ecomWorldPayClient.createUpiIntent(any())).thenReturn(Mono.just(qr));

            StepVerifier.create(service.initiatePayment(upiRequest("310.00"), CUSTOMER))
                    .assertNext(r -> assertThat(r.getAmount()).isEqualByComparingTo("310.00"))
                    .verifyComplete();
        }
    }

    // --- link order ---------------------------------------------------------------------------

    @Nested
    class LinkOrder {

        private Payment unlinked() {
            Payment p = processingUpi();
            p.setOrderId(null);
            return p;
        }

        private void order(long userId, String total, String status) {
            when(orderServiceClient.getOrder(100L)).thenReturn(Mono.just(OrderSnapshotDto.builder()
                    .orderId(100L).userId(userId).totalAmount(new BigDecimal(total)).status(status).build()));
        }

        @Test
        void linksOwnPaymentToOwnOrderWithMatchingTotal() {
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(unlinked()));
            order(CUSTOMER, "302.50", "CREATED");
            StepVerifier.create(service.linkOrder(11L, 100L, CUSTOMER))
                    .assertNext(r -> assertThat(r.getOrderId()).isEqualTo(100L))
                    .verifyComplete();
        }

        @Test
        void onePaisaRoundingDifferenceIsAccepted() {
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(unlinked()));
            order(CUSTOMER, "302.51", "CREATED");
            StepVerifier.create(service.linkOrder(11L, 100L, CUSTOMER))
                    .assertNext(r -> assertThat(r.getOrderId()).isEqualTo(100L))
                    .verifyComplete();
        }

        @Test
        void oneRupeePaymentCannotBeLinkedToAnExpensiveOrder() {
            Payment cheap = unlinked();
            cheap.setAmount(new BigDecimal("1.00"));
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(cheap));
            order(CUSTOMER, "3000.00", "CREATED");
            StepVerifier.create(service.linkOrder(11L, 100L, CUSTOMER))
                    .expectErrorSatisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT))
                    .verify();
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void cannotLinkToSomeoneElsesOrder() {
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(unlinked()));
            order(OTHER_USER, "302.50", "CREATED");
            StepVerifier.create(service.linkOrder(11L, 100L, CUSTOMER))
                    .expectError(ResponseStatusException.class).verify();
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void cannotLinkSomeoneElsesPayment() {
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(unlinked()));
            StepVerifier.create(service.linkOrder(11L, 100L, OTHER_USER))
                    .expectError(ResponseStatusException.class).verify();
            verifyNoInteractions(orderServiceClient);
        }

        @Test
        void cannotLinkToAnOrderThatIsNoLongerAwaitingPayment() {
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(unlinked()));
            order(CUSTOMER, "302.50", "PAID");
            StepVerifier.create(service.linkOrder(11L, 100L, CUSTOMER))
                    .expectError(ResponseStatusException.class).verify();
        }

        @Test
        void relinkingTheSameOrderIsIdempotent() {
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(processingUpi()));
            StepVerifier.create(service.linkOrder(11L, 100L, CUSTOMER))
                    .assertNext(r -> assertThat(r.getOrderId()).isEqualTo(100L))
                    .verifyComplete();
            verifyNoInteractions(orderServiceClient);
        }
    }

    // --- gateway callback / status (successful + failed payment) ------------------------------

    @Nested
    class GatewayOutcome {

        @Test
        void forgedSuccessCallbackIsIgnoredWhenGatewaySaysNotPaid() {
            Payment p = processingUpi();
            when(paymentRepository.findByTransactionId("inv-uuid-1")).thenReturn(Mono.just(p));
            when(ecomWorldPayClient.checkTransactionStatus("PG123"))
                    .thenReturn(Mono.error(new TransactionNotYetAvailableException("pending")));

            StepVerifier.create(service.handleGatewayCallback(gateway("SUCCESS", "302.50", "inv-uuid-1", "PG123")))
                    .verifyComplete();

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
            verify(orderServiceClient, never()).updatePaymentStatus(anyLong(), eq("PAID"), any(), any(), any());
            verify(paymentKafkaProducer, never()).publishPaymentCompleted(any());
        }

        @Test
        void callbackIsOnlyASignal_statusIsPulledFromTheGateway() {
            Payment p = processingUpi();
            when(paymentRepository.findByTransactionId("inv-uuid-1")).thenReturn(Mono.just(p));
            // the callback body claims FAILED, the gateway's own answer is SUCCESS — the gateway wins
            when(ecomWorldPayClient.checkTransactionStatus("PG123"))
                    .thenReturn(Mono.just(gateway("SUCCESS", "302.50", "inv-uuid-1", "PG123")));

            StepVerifier.create(service.handleGatewayCallback(gateway("FAILED", "302.50", "inv-uuid-1", "PG123")))
                    .verifyComplete();

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        void verifiedSuccessfulPaymentMarksOrderPaid() {
            Payment p = processingUpi();
            when(paymentRepository.findByTransactionId("inv-uuid-1")).thenReturn(Mono.just(p));
            when(ecomWorldPayClient.checkTransactionStatus("PG123"))
                    .thenReturn(Mono.just(gateway("SUCCESS", "302.50", "inv-uuid-1", "PG123")));

            StepVerifier.create(service.handleGatewayCallback(gateway("SUCCESS", "302.50", "inv-uuid-1", "PG123")))
                    .verifyComplete();

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(p.getPaidAt()).isNotNull();
            assertThat(p.getRrn()).isEqualTo("RRN1");
            verify(orderServiceClient).updatePaymentStatus(eq(100L), eq("PAID"), eq("UPI"), eq("inv-uuid-1"), any());
            verify(paymentKafkaProducer).publishPaymentCompleted(argThat(e -> "PAID".equals(e.getStatus())));
        }

        @Test
        void underpaidSuccessIsNotAcceptedAndIsFlaggedFailed() {
            Payment p = processingUpi();
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(p));
            when(ecomWorldPayClient.checkTransactionStatus("PG123"))
                    .thenReturn(Mono.just(gateway("SUCCESS", "1.00", "inv-uuid-1", "PG123")));

            StepVerifier.create(service.refreshGatewayStatus(11L, CUSTOMER, false))
                    .assertNext(r -> {
                        assertThat(r.getStatus()).isEqualTo(PaymentStatus.FAILED);
                        assertThat(r.getFailureReason()).contains("could not be verified");
                    })
                    .verifyComplete();
            verify(orderServiceClient, never()).updatePaymentStatus(anyLong(), eq("PAID"), any(), any(), any());
        }

        @Test
        void successForADifferentInvoiceIsNotAccepted() {
            Payment p = processingUpi();
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(p));
            when(ecomWorldPayClient.checkTransactionStatus("PG123"))
                    .thenReturn(Mono.just(gateway("SUCCESS", "302.50", "someone-elses-invoice", "PG123")));

            StepVerifier.create(service.refreshGatewayStatus(11L, CUSTOMER, false))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(PaymentStatus.FAILED))
                    .verifyComplete();
            verify(orderServiceClient, never()).updatePaymentStatus(anyLong(), eq("PAID"), any(), any(), any());
        }

        @Test
        void successWithoutAnAmountStaysProcessing() {
            Payment p = processingUpi();
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(p));
            when(ecomWorldPayClient.checkTransactionStatus("PG123"))
                    .thenReturn(Mono.just(gateway("SUCCESS", null, "inv-uuid-1", "PG123")));

            StepVerifier.create(service.refreshGatewayStatus(11L, CUSTOMER, false))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(PaymentStatus.PROCESSING))
                    .verifyComplete();
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void failedPaymentCancelsTheOrder() {
            Payment p = processingUpi();
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(p));
            when(ecomWorldPayClient.checkTransactionStatus("PG123"))
                    .thenReturn(Mono.just(gateway("FAILED", "302.50", "inv-uuid-1", "PG123")));

            StepVerifier.create(service.refreshGatewayStatus(11L, CUSTOMER, false))
                    .assertNext(r -> {
                        assertThat(r.getStatus()).isEqualTo(PaymentStatus.FAILED);
                        assertThat(r.getFailureReason()).isEqualTo("Declined by bank");
                    })
                    .verifyComplete();
            verify(orderServiceClient).updatePaymentStatus(100L, "CANCELLED");
        }

        @Test
        void callbackForAlreadySettledPaymentDoesNotCallTheGateway() {
            Payment p = processingUpi();
            p.setStatus(PaymentStatus.SUCCESS);
            when(paymentRepository.findByTransactionId("inv-uuid-1")).thenReturn(Mono.just(p));
            StepVerifier.create(service.handleGatewayCallback(gateway("FAILED", "302.50", "inv-uuid-1", "PG123")))
                    .verifyComplete();
            verify(ecomWorldPayClient, never()).checkTransactionStatus(any());
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        void callbackForUnknownInvoiceIsAckedQuietly() {
            when(paymentRepository.findByTransactionId("nope")).thenReturn(Mono.empty());
            StepVerifier.create(service.handleGatewayCallback(gateway("SUCCESS", "302.50", "nope", "PG9")))
                    .verifyComplete();
        }

        @Test
        void gatewayOutageDuringCallbackIsAckedWithoutChange() {
            Payment p = processingUpi();
            when(paymentRepository.findByTransactionId("inv-uuid-1")).thenReturn(Mono.just(p));
            when(ecomWorldPayClient.checkTransactionStatus("PG123")).thenReturn(Mono.error(new RuntimeException("timeout")));
            StepVerifier.create(service.handleGatewayCallback(gateway("SUCCESS", "302.50", "inv-uuid-1", "PG123")))
                    .verifyComplete();
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        }
    }

    // --- access to payment records --------------------------------------------------------------

    @Nested
    class Access {

        @Test
        void ownerCanPollOwnPayment() {
            Payment p = processingUpi();
            p.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(p));
            StepVerifier.create(service.getPayment(11L, CUSTOMER, false))
                    .assertNext(r -> assertThat(r.getPaymentId()).isEqualTo(11L))
                    .verifyComplete();
        }

        @Test
        void strangerCannotReadOrRefreshAPayment() {
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(processingUpi()));
            StepVerifier.create(service.getPayment(11L, OTHER_USER, false))
                    .expectError(ResponseStatusException.class).verify();
            StepVerifier.create(service.refreshGatewayStatus(11L, OTHER_USER, false))
                    .expectError(ResponseStatusException.class).verify();
            verify(ecomWorldPayClient, never()).checkTransactionStatus(any());
        }

        @Test
        void adminCanReadAnyPayment() {
            Payment p = processingUpi();
            p.setStatus(PaymentStatus.SUCCESS);
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(p));
            StepVerifier.create(service.getPayment(11L, 1L, true))
                    .assertNext(r -> assertThat(r.getPaymentId()).isEqualTo(11L))
                    .verifyComplete();
        }

        @Test
        void paymentsByOrderAreFilteredToTheCallerUnlessPrivileged() {
            Payment mine = processingUpi();
            Payment theirs = processingUpi();
            theirs.setId(12L);
            theirs.setUserId(OTHER_USER);
            when(paymentRepository.findByOrderId(100L)).thenReturn(Flux.just(mine, theirs));
            StepVerifier.create(service.getPaymentsByOrder(100L, CUSTOMER, false).collectList())
                    .assertNext(list -> assertThat(list).extracting("paymentId").containsExactly(11L))
                    .verifyComplete();
            when(paymentRepository.findByOrderId(100L)).thenReturn(Flux.just(mine, theirs));
            StepVerifier.create(service.getPaymentsByOrder(100L, null, true).collectList())
                    .assertNext(list -> assertThat(list).hasSize(2))
                    .verifyComplete();
        }
    }

    // --- admin overrides + refunds -------------------------------------------------------------

    @Nested
    class AdminAndRefunds {

        @Test
        void adminMarksCodPaymentCollected() {
            Payment cod = processingUpi();
            cod.setMethod(PaymentMethod.COD);
            cod.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(cod));
            StepVerifier.create(service.markPaymentSuccess(11L))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(PaymentStatus.SUCCESS))
                    .verifyComplete();
            verify(orderServiceClient).updatePaymentStatus(eq(100L), eq("PAID"), eq("COD"), any(), any());
        }

        @Test
        void partialThenFullRefund() {
            Payment paid = processingUpi();
            paid.setStatus(PaymentStatus.SUCCESS);
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(paid));

            StepVerifier.create(service.refundPayment(11L, new BigDecimal("100.00")))
                    .assertNext(r -> assertThat(r.getRefundAmount()).isEqualByComparingTo("100.00"))
                    .verifyComplete();
            verify(orderServiceClient, never()).updatePaymentStatus(anyLong(), eq("REFUNDED"));

            StepVerifier.create(service.refundPayment(11L, new BigDecimal("202.50")))
                    .assertNext(r -> assertThat(r.getRefundAmount()).isEqualByComparingTo("302.50"))
                    .verifyComplete();
            verify(orderServiceClient).updatePaymentStatus(100L, "REFUNDED");
        }

        @Test
        void refundCannotExceedWhatWasPaid() {
            Payment paid = processingUpi();
            paid.setStatus(PaymentStatus.SUCCESS);
            when(paymentRepository.findById(11L)).thenReturn(Mono.just(paid));
            StepVerifier.create(service.refundPayment(11L, new BigDecimal("400.00")))
                    .expectError(ResponseStatusException.class).verify();
        }

        @Test
        void orderRefundSyncRefundsTheRemainder() {
            Payment paid = processingUpi();
            paid.setStatus(PaymentStatus.SUCCESS);
            when(paymentRepository.findByOrderId(100L)).thenReturn(Flux.just(paid));
            StepVerifier.create(service.refundByOrder(100L)).verifyComplete();
            assertThat(paid.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(paid.getRefundAmount()).isEqualByComparingTo("302.50");
        }
    }
}
