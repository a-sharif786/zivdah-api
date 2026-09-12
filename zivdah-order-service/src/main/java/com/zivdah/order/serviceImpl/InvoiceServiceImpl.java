package com.zivdah.order.serviceImpl;

import com.zivdah.order.client.AuthServiceClient;
import com.zivdah.order.client.dto.CustomerInfoDto;
import com.zivdah.order.dto.GenerateInvoiceRequestDto;
import com.zivdah.order.dto.InvoiceResponseDto;
import com.zivdah.order.entity.Invoice;
import com.zivdah.order.entity.Order;
import com.zivdah.order.entity.OrderItem;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.pdf.InvoicePdfData;
import com.zivdah.order.pdf.InvoicePdfGenerator;
import com.zivdah.order.repository.InvoiceNumberGenerator;
import com.zivdah.order.repository.InvoiceRepository;
import com.zivdah.order.repository.OrderItemRepository;
import com.zivdah.order.repository.OrderRepository;
import com.zivdah.order.service.InvoiceService;
import com.zivdah.order.storage.InvoiceStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    // Statuses in which payment has definitely not gone through yet — generateInvoice() refuses
    // these (requirement: never generate a PAID invoice when payment hasn't/hasn't yet
    // succeeded). Every later status (PAID onward, including REFUNDED) implies payment was
    // completed at some point in the order's life, so a retroactive invoice is still valid there.
    private static final Set<OrderStatus> NOT_YET_PAID = EnumSet.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING);

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final AuthServiceClient authServiceClient;
    private final InvoicePdfGenerator invoicePdfGenerator;
    private final InvoiceStorageService invoiceStorageService;

    @Override
    public Mono<InvoiceResponseDto> generateInvoice(Long orderId, GenerateInvoiceRequestDto request) {
        GenerateInvoiceRequestDto req = request != null ? request : GenerateInvoiceRequestDto.builder().build();
        return invoiceRepository.findByOrderId(orderId)
                .flatMap(existing -> req.isForce()
                        ? regenerate(existing, req.getPaymentMethod(), req.getTransactionId(), req.getPaidAt())
                        : Mono.just(existing))
                .switchIfEmpty(Mono.defer(() -> createInvoice(orderId, req.getPaymentMethod(), req.getTransactionId(), req.getPaidAt())))
                .map(this::mapToResponse);
    }

    @Override
    public Mono<InvoiceResponseDto> generateInvoice(Long orderId, String paymentMethod, String transactionId, LocalDateTime paidAt) {
        return generateInvoice(orderId, GenerateInvoiceRequestDto.builder()
                .paymentMethod(paymentMethod).transactionId(transactionId).paidAt(paidAt).build());
    }

    private Mono<Invoice> createInvoice(Long orderId, String paymentMethod, String transactionId, LocalDateTime paidAt) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId)))
                .flatMap(order -> validatePayable(order).thenReturn(order))
                .flatMap(order -> Mono.zip(
                                orderItemRepository.findByOrderId(orderId).collectList(),
                                authServiceClient.getCustomerInfo(order.getUserId()),
                                invoiceNumberGenerator.nextInvoiceNumber())
                        .flatMap(tuple -> {
                            List<OrderItem> items = tuple.getT1();
                            CustomerInfoDto customer = tuple.getT2();
                            String invoiceNumber = tuple.getT3();
                            LocalDateTime invoiceDate = paidAt != null ? paidAt : LocalDateTime.now();

                            InvoicePdfData pdfData = buildPdfData(
                                    order, items, customer, invoiceNumber, invoiceDate, "PAID", paymentMethod, transactionId);

                            return renderAndStore(invoiceNumber, pdfData)
                                    .flatMap(stored -> invoiceRepository.save(Invoice.builder()
                                            .invoiceNumber(invoiceNumber)
                                            .orderId(order.getId())
                                            .customerId(order.getUserId())
                                            .customerName(customer.getName())
                                            .customerEmail(customer.getEmail())
                                            .subtotal(orZero(order.getSubTotal()))
                                            .discount(orZero(order.getDiscountAmount()))
                                            .deliveryFee(orZero(order.getDeliveryCharge()))
                                            .tax(orZero(order.getTotalTaxAmount()))
                                            .totalAmount(orZero(order.getTotalAmount()))
                                            .paymentStatus("PAID")
                                            .paymentMethod(paymentMethod)
                                            .transactionId(transactionId)
                                            .invoiceDate(invoiceDate)
                                            .pdfUrl(stored.publicUrl())
                                            .createdAt(LocalDateTime.now())
                                            .build()))
                                    .doOnSuccess(inv -> log.info("Invoice {} generated for order {}", inv.getInvoiceNumber(), orderId))
                                    // Idempotency race guard: invoices.order_id is UNIQUE (see V3
                                    // migration) — if two concurrent requests both found "no existing
                                    // invoice" for this order, the loser's insert fails here instead of
                                    // creating a duplicate row. Fall back to whichever row won the race.
                                    .onErrorResume(DataIntegrityViolationException.class,
                                            ex -> invoiceRepository.findByOrderId(orderId).switchIfEmpty(Mono.error(ex)));
                        }));
    }

    private Mono<Invoice> regenerate(Invoice existing, String paymentMethod, String transactionId, LocalDateTime paidAt) {
        return orderRepository.findById(existing.getOrderId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + existing.getOrderId())))
                .flatMap(order -> Mono.zip(
                        orderItemRepository.findByOrderId(order.getId()).collectList(),
                        authServiceClient.getCustomerInfo(order.getUserId())
                ).flatMap(tuple -> {
                    List<OrderItem> items = tuple.getT1();
                    CustomerInfoDto customer = tuple.getT2();
                    String method = paymentMethod != null ? paymentMethod : existing.getPaymentMethod();
                    String txn = transactionId != null ? transactionId : existing.getTransactionId();
                    LocalDateTime invoiceDate = paidAt != null ? paidAt : existing.getInvoiceDate();

                    // Same invoice number as before — a forced regeneration replaces the PDF file
                    // and refreshes the row, it never mints a second invoice for the same order.
                    InvoicePdfData pdfData = buildPdfData(
                            order, items, customer, existing.getInvoiceNumber(), invoiceDate, "PAID", method, txn);

                    return renderAndStore(existing.getInvoiceNumber(), pdfData)
                            .flatMap(stored -> {
                                existing.setCustomerName(customer.getName());
                                existing.setCustomerEmail(customer.getEmail());
                                existing.setSubtotal(orZero(order.getSubTotal()));
                                existing.setDiscount(orZero(order.getDiscountAmount()));
                                existing.setDeliveryFee(orZero(order.getDeliveryCharge()));
                                existing.setTax(orZero(order.getTotalTaxAmount()));
                                existing.setTotalAmount(orZero(order.getTotalAmount()));
                                existing.setPaymentMethod(method);
                                existing.setTransactionId(txn);
                                existing.setInvoiceDate(invoiceDate);
                                existing.setPdfUrl(stored.publicUrl());
                                existing.setUpdatedAt(LocalDateTime.now());
                                return invoiceRepository.save(existing);
                            });
                }))
                .doOnSuccess(inv -> log.info("Invoice {} regenerated for order {}", inv.getInvoiceNumber(), inv.getOrderId()));
    }

    private Mono<Void> validatePayable(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot generate an invoice for a cancelled order"));
        }
        if (NOT_YET_PAID.contains(order.getStatus())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment has not been completed for this order yet"));
        }
        return Mono.empty();
    }

    // PDF rendering (PDFBox, CPU-bound) and the file write are both blocking work, so both run
    // together on boundedElastic rather than the WebFlux event-loop threads.
    private Mono<InvoiceStorageService.StoredInvoiceFile> renderAndStore(String invoiceNumber, InvoicePdfData pdfData) {
        return Mono.fromCallable(() -> invoicePdfGenerator.generate(pdfData))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> invoiceStorageService.save(invoiceNumber, bytes));
    }

    private InvoicePdfData buildPdfData(Order order, List<OrderItem> items, CustomerInfoDto customer,
                                         String invoiceNumber, LocalDateTime invoiceDate,
                                         String paymentStatus, String paymentMethod, String transactionId) {
        List<InvoicePdfData.LineItem> lineItems = items.stream()
                .map(i -> InvoicePdfData.LineItem.builder()
                        .productName(i.getProductName() != null ? i.getProductName() : "Product #" + i.getProductId())
                        .quantity(i.getQuantity() != null ? i.getQuantity() : 0)
                        .unitPrice(orZero(i.getPrice()))
                        // No per-line discount is tracked on OrderItem today — only the order-level
                        // discountAmount, which is shown once in the totals block below.
                        .discount(BigDecimal.ZERO)
                        .itemTotal(orZero(i.getTotalAmount() != null ? i.getTotalAmount() : i.getSubtotal()))
                        .build())
                .collect(Collectors.toList());

        return InvoicePdfData.builder()
                .invoiceNumber(invoiceNumber)
                .invoiceDate(invoiceDate)
                .orderNumber(order.getOrderNumber())
                .orderId(order.getId())
                .customerName(customer.getName())
                .customerEmail(customer.getEmail())
                .customerMobile(customer.getMobile())
                .addressLine1(order.getDeliveryAddressLine1())
                .addressLine2(order.getDeliveryAddressLine2())
                .city(order.getDeliveryCity())
                .state(order.getDeliveryState())
                .pinCode(order.getDeliveryPinCode())
                .country(order.getDeliveryCountry())
                .items(lineItems)
                .subtotal(orZero(order.getSubTotal()))
                .discount(orZero(order.getDiscountAmount()))
                .deliveryFee(orZero(order.getDeliveryCharge()))
                .tax(orZero(order.getTotalTaxAmount()))
                .totalAmount(orZero(order.getTotalAmount()))
                .currency(order.getCurrency())
                .paymentStatus(paymentStatus)
                .paymentMethod(paymentMethod)
                .transactionId(transactionId)
                .build();
    }

    @Override
    public Mono<InvoiceResponseDto> getById(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + invoiceId)))
                .map(this::mapToResponse);
    }

    @Override
    public Mono<InvoiceResponseDto> getByOrderId(Long orderId) {
        return invoiceRepository.findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No invoice found for order: " + orderId)))
                .map(this::mapToResponse);
    }

    @Override
    public Flux<InvoiceResponseDto> getByCustomer(Long customerId, Pageable pageable) {
        return invoiceRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId, pageable).map(this::mapToResponse);
    }

    @Override
    public Flux<InvoiceResponseDto> getAll(Pageable pageable) {
        return invoiceRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::mapToResponse);
    }

    @Override
    public Mono<byte[]> getPdfBytes(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + invoiceId)))
                .flatMap(invoice -> invoiceStorageService.read(invoice.getInvoiceNumber()));
    }

    @Override
    public Mono<Boolean> canAccess(Long invoiceId, Long currentUserId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return Mono.just(true);
        }
        return invoiceRepository.findById(invoiceId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + invoiceId)))
                .flatMap(invoice -> {
                    if (invoice.getCustomerId().equals(currentUserId)) {
                        return Mono.just(true);
                    }
                    if ("VENDOR".equalsIgnoreCase(role)) {
                        // Same ownership rule already used for order access itself (see
                        // OrderServiceImpl#getOrdersByVendor) — a vendor may view the invoice if
                        // they own at least one item on the underlying (possibly multi-vendor) order.
                        // Note: like GET /orders/{orderId} today, this doesn't filter the PDF/response
                        // down to just that vendor's items — it's whole-order visibility or none.
                        return orderItemRepository.findByOrderId(invoice.getOrderId())
                                .any(item -> currentUserId.equals(item.getVendorId()));
                    }
                    return Mono.just(false);
                });
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private InvoiceResponseDto mapToResponse(Invoice invoice) {
        return InvoiceResponseDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .orderId(invoice.getOrderId())
                .customerId(invoice.getCustomerId())
                .customerName(invoice.getCustomerName())
                .customerEmail(invoice.getCustomerEmail())
                .subtotal(invoice.getSubtotal())
                .discount(invoice.getDiscount())
                .deliveryFee(invoice.getDeliveryFee())
                .tax(invoice.getTax())
                .totalAmount(invoice.getTotalAmount())
                .paymentStatus(invoice.getPaymentStatus())
                .paymentMethod(invoice.getPaymentMethod())
                .transactionId(invoice.getTransactionId())
                .invoiceDate(invoice.getInvoiceDate())
                .pdfUrl(invoice.getPdfUrl())
                .downloadUrl("/restful/v1/api/invoices/" + invoice.getId() + "/download")
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .build();
    }
}
