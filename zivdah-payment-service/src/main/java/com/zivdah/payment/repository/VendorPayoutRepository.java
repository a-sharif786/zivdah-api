package com.zivdah.payment.repository;

import com.zivdah.payment.entity.VendorPayout;
import com.zivdah.payment.enums.VendorPayoutStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VendorPayoutRepository extends ReactiveCrudRepository<VendorPayout, Long> {
    Flux<VendorPayout> findByVendorIdOrderByRequestedAtDesc(Long vendorId);
    Flux<VendorPayout> findAllByOrderByRequestedAtDesc();
    Flux<VendorPayout> findByStatusOrderByRequestedAtDesc(VendorPayoutStatus status);

    // Matched against a payout's gatewayReferenceId — see VendorPayoutServiceImpl's callback
    // handler, which is called with EcomWorldPay's own "TransactionId" field.
    Mono<VendorPayout> findByGatewayReferenceId(String gatewayReferenceId);
}
