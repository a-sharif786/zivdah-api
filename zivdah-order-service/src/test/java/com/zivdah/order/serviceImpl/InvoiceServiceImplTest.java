package com.zivdah.order.serviceImpl;

import com.zivdah.order.client.AuthServiceClient;
import com.zivdah.order.client.dto.CustomerInfoDto;
import com.zivdah.order.dto.GenerateInvoiceRequestDto;
import com.zivdah.order.dto.InvoiceResponseDto;
import com.zivdah.order.entity.Invoice;
import com.zivdah.order.entity.Order;
import com.zivdah.order.entity.OrderItem;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.pdf.InvoicePdfGenerator;
import com.zivdah.order.repository.InvoiceNumberGenerator;
import com.zivdah.order.repository.InvoiceRepository;
import com.zivdah.order.repository.OrderItemRepository;
import com.zivdah.order.repository.OrderRepository;
import com.zivdah.order.storage.InvoiceStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private InvoiceNumberGenerator invoiceNumberGenerator;
    @Mock private AuthServiceClient authServiceClient;
    @Mock private InvoicePdfGenerator invoicePdfGenerator;
    @Mock private InvoiceStorageService invoiceStorageService;

    private InvoiceServiceImpl invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceServiceImpl(
                invoiceRepository, orderRepository, orderItemRepository,
                invoiceNumberGenerator, authServiceClient, invoicePdfGenerator, invoiceStorageService);
    }

    private Order paidOrder() {
        return Order.builder()
                .id(42L).userId(7L).orderNumber("ORD-0042")
                .status(OrderStatus.PAID)
                .subTotal(new BigDecimal("100.00"))
                .discountAmount(new BigDecimal("10.00"))
                .deliveryCharge(new BigDecimal("5.00"))
                .totalTaxAmount(new BigDecimal("9.00"))
                .totalAmount(new BigDecimal("104.00"))
                .currency("INR")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // --- idempotency -----------------------------------------------------------------------

    @Test
    void generateInvoice_returnsExistingInvoice_whenOneAlreadyExistsForTheOrder() {
        Invoice existing = existingInvoice();
        when(invoiceRepository.findByOrderId(42L)).thenReturn(Mono.just(existing));

        StepVerifier.create(invoiceService.generateInvoice(42L, GenerateInvoiceRequestDto.builder().build()))
                .expectNextMatches(dto -> dto.getInvoiceNumber().equals("INV-2026-000001"))
                .verifyComplete();

        // No new invoice number minted, no PDF re-rendered, no re-save — it's a pure read.
        verifyNoInteractions(invoiceNumberGenerator, invoicePdfGenerator, invoiceStorageService, orderRepository);
        verify(invoiceRepository, never()).save(any());
    }

    // --- error paths -------------------------------------------------------------------------

    @Test
    void generateInvoice_failsWithNotFound_whenOrderDoesNotExist() {
        when(invoiceRepository.findByOrderId(999L)).thenReturn(Mono.empty());
        when(orderRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(invoiceService.generateInvoice(999L, GenerateInvoiceRequestDto.builder().build()))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException
                        && ((ResponseStatusException) ex).getStatusCode().value() == 404)
                .verify();
    }

    @Test
    void generateInvoice_rejectsOrder_whenPaymentNotYetCompleted() {
        Order pending = paidOrder();
        pending.setStatus(OrderStatus.PAYMENT_PENDING);
        when(invoiceRepository.findByOrderId(42L)).thenReturn(Mono.empty());
        when(orderRepository.findById(42L)).thenReturn(Mono.just(pending));

        StepVerifier.create(invoiceService.generateInvoice(42L, GenerateInvoiceRequestDto.builder().build()))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException
                        && ((ResponseStatusException) ex).getStatusCode().value() == 400)
                .verify();

        verifyNoInteractions(invoicePdfGenerator, invoiceStorageService);
    }

    @Test
    void generateInvoice_rejectsOrder_whenCancelled() {
        Order cancelled = paidOrder();
        cancelled.setStatus(OrderStatus.CANCELLED);
        when(invoiceRepository.findByOrderId(42L)).thenReturn(Mono.empty());
        when(orderRepository.findById(42L)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(invoiceService.generateInvoice(42L, GenerateInvoiceRequestDto.builder().build()))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException
                        && ((ResponseStatusException) ex).getStatusCode().value() == 400)
                .verify();
    }

    // --- happy path --------------------------------------------------------------------------

    @Test
    void generateInvoice_createsAndPersistsInvoice_forANewlyPaidOrder() {
        Order order = paidOrder();
        OrderItem item = OrderItem.builder()
                .id(1L).orderId(42L).productId(5L).productName("Organic Apples")
                .quantity(2).price(new BigDecimal("50.00")).subtotal(new BigDecimal("100.00"))
                .totalAmount(new BigDecimal("100.00"))
                .build();
        CustomerInfoDto customer = CustomerInfoDto.builder().id(7L).name("Jane Doe").email("jane@example.com").build();

        when(invoiceRepository.findByOrderId(42L)).thenReturn(Mono.empty());
        when(orderRepository.findById(42L)).thenReturn(Mono.just(order));
        when(orderItemRepository.findByOrderId(42L)).thenReturn(Flux.just(item));
        when(authServiceClient.getCustomerInfo(7L)).thenReturn(Mono.just(customer));
        when(invoiceNumberGenerator.nextInvoiceNumber()).thenReturn(Mono.just("INV-2026-000002"));
        when(invoicePdfGenerator.generate(any())).thenReturn(new byte[]{1, 2, 3});
        when(invoiceStorageService.save(eq("INV-2026-000002"), any()))
                .thenReturn(Mono.just(new InvoiceStorageService.StoredInvoiceFile(
                        "https://zivdahonlinegrocery.com/media/invoices/INV-2026-000002.pdf")));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice toSave = inv.getArgument(0);
            toSave.setId(1L);
            return Mono.just(toSave);
        });

        StepVerifier.create(invoiceService.generateInvoice(42L, "UPI", "txn-123", null))
                .expectNextMatches(dto ->
                        dto.getInvoiceNumber().equals("INV-2026-000002")
                                && dto.getOrderId().equals(42L)
                                && dto.getCustomerName().equals("Jane Doe")
                                && dto.getPaymentMethod().equals("UPI")
                                && dto.getTotalAmount().compareTo(new BigDecimal("104.00")) == 0
                                && dto.getDownloadUrl().equals("/restful/v1/api/invoices/1/download"))
                .verifyComplete();

        verify(invoiceStorageService).save(eq("INV-2026-000002"), any());
        verify(invoiceRepository).save(any(Invoice.class));
    }

    // --- authorization -----------------------------------------------------------------------

    @Test
    void canAccess_true_forAdmin_regardlessOfOwnership() {
        StepVerifier.create(invoiceService.canAccess(1L, 999L, "ADMIN"))
                .expectNext(true)
                .verifyComplete();
        verifyNoInteractions(invoiceRepository);
    }

    @Test
    void canAccess_true_forTheInvoicesOwnCustomer() {
        Invoice invoice = existingInvoice();
        when(invoiceRepository.findById(1L)).thenReturn(Mono.just(invoice));

        StepVerifier.create(invoiceService.canAccess(1L, 7L, "USER"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void canAccess_true_forAVendorWithAnItemOnTheOrder() {
        Invoice invoice = existingInvoice();
        when(invoiceRepository.findById(1L)).thenReturn(Mono.just(invoice));
        when(orderItemRepository.findByOrderId(42L)).thenReturn(Flux.just(
                OrderItem.builder().vendorId(55L).build()));

        StepVerifier.create(invoiceService.canAccess(1L, 55L, "VENDOR"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void canAccess_false_forAnUnrelatedUser() {
        Invoice invoice = existingInvoice();
        when(invoiceRepository.findById(1L)).thenReturn(Mono.just(invoice));

        StepVerifier.create(invoiceService.canAccess(1L, 12345L, "USER"))
                .expectNext(false)
                .verifyComplete();
    }

    private Invoice existingInvoice() {
        return Invoice.builder()
                .id(1L).invoiceNumber("INV-2026-000001").orderId(42L).customerId(7L)
                .customerName("Jane Doe").customerEmail("jane@example.com")
                .subtotal(new BigDecimal("100.00")).discount(new BigDecimal("10.00"))
                .deliveryFee(new BigDecimal("5.00")).tax(new BigDecimal("9.00"))
                .totalAmount(new BigDecimal("104.00"))
                .paymentStatus("PAID").paymentMethod("UPI")
                .invoiceDate(LocalDateTime.now())
                .pdfUrl("https://zivdahonlinegrocery.com/media/invoices/INV-2026-000001.pdf")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
