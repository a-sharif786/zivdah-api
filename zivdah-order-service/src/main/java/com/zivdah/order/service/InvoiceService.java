package com.zivdah.order.service;

import com.zivdah.order.dto.GenerateInvoiceRequestDto;
import com.zivdah.order.dto.InvoiceResponseDto;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface InvoiceService {

    // Idempotent unless request.force() is set: if an invoice already exists for the order,
    // it's returned as-is (requirement: no duplicate invoice generation). Errors with 404 if
    // the order doesn't exist, and 409/400 if the order was never paid (see
    // OrderStatus-based check in the impl) — never generates a PAID invoice for a
    // not-yet-paid or cancelled order.
    Mono<InvoiceResponseDto> generateInvoice(Long orderId, GenerateInvoiceRequestDto request);

    // Narrow overload for the automatic hook from OrderServiceImpl#updatePaymentStatus — same
    // semantics as above, just without a request body to build.
    Mono<InvoiceResponseDto> generateInvoice(Long orderId, String paymentMethod, String transactionId, LocalDateTime paidAt);

    Mono<InvoiceResponseDto> getById(Long invoiceId);
    Mono<InvoiceResponseDto> getByOrderId(Long orderId);
    Flux<InvoiceResponseDto> getByCustomer(Long customerId, Pageable pageable);
    Flux<InvoiceResponseDto> getAll(Pageable pageable);

    // Raw PDF bytes for the download/view endpoint — re-reads from disk each time rather than
    // caching, since the file itself (not its bytes) is the thing InvoiceStorageService owns.
    Mono<byte[]> getPdfBytes(Long invoiceId);

    // Authorization: true if currentUserId/role may access this invoice — owner, ADMIN, or a
    // VENDOR who owns at least one item on the underlying order (same rule already used for
    // order access — see OrderServiceImpl#getOrdersByVendor).
    Mono<Boolean> canAccess(Long invoiceId, Long currentUserId, String role);
}
