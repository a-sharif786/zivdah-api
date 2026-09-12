package com.zivdah.order.repository;

import com.zivdah.order.entity.Invoice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InvoiceRepository extends ReactiveCrudRepository<Invoice, Long> {
    Mono<Invoice> findByOrderId(Long orderId);

    // OrderByCreatedAtDesc: newest invoice first, same convention as every other list in this
    // codebase (Orders, Products, Payments, ...).
    Flux<Invoice> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
    Flux<Invoice> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
