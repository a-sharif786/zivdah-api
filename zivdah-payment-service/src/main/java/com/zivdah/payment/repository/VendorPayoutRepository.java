package com.zivdah.payment.repository;

import com.zivdah.payment.entity.VendorPayout;
import com.zivdah.payment.enums.VendorPayoutStatus;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collection;

public interface VendorPayoutRepository extends ReactiveCrudRepository<VendorPayout, Long> {
    Flux<VendorPayout> findByVendorIdOrderByRequestedAtDesc(Long vendorId);
    Flux<VendorPayout> findAllByOrderByRequestedAtDesc();
    Flux<VendorPayout> findByStatusOrderByRequestedAtDesc(VendorPayoutStatus status);

    // Matched against a payout's gatewayReferenceId — see VendorPayoutServiceImpl's callback
    // handler, which is called with EcomWorldPay's own "TransactionId" field.
    Mono<VendorPayout> findByGatewayReferenceId(String gatewayReferenceId);

    // Pre-check for a friendly duplicate-payout error — mirrors uq_vendor_payouts_open_per_vendor
    // (V7), which is the actual race-proof guard; this just lets us fail with a clear message
    // instead of a raw constraint-violation error in the common (non-racing) case.
    Mono<Boolean> existsByVendorIdAndStatusIn(Long vendorId, Collection<VendorPayoutStatus> statuses);

    // Atomic compare-and-swap closing the approvePayout TOCTOU race: two concurrent approve
    // calls for the same payout can only have one of them return rowsUpdated > 0, so only one
    // can ever proceed to actually submit to the gateway. Same @Modifying @Query pattern as
    // zivdah-user-service's UserAddressRepository#resetDefaultAddress.
    @Modifying
    @Query("UPDATE vendor_payouts SET status = 'PROCESSING', updated_at = :now WHERE id = :id AND status = 'REQUESTED'")
    Mono<Integer> markProcessingIfRequested(Long id, LocalDateTime now);
}
