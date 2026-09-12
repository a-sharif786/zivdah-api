package com.zivdah.delivery.repository;

import com.zivdah.delivery.entity.Delivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeliveryRepository extends ReactiveCrudRepository<Delivery, Long> {
    Mono<Delivery> findByOrderIdAndVendorId(Long orderId, Long vendorId);
    Flux<Delivery> findByOrderId(Long orderId);
    // OrderByCreatedAtDesc: vendor/delivery-boy delivery lists should show new deliveries on top.
    Flux<Delivery> findByVendorIdOrderByCreatedAtDesc(Long vendorId, Pageable pageable);
    Flux<Delivery> findByDeliveryBoyIdOrderByCreatedAtDesc(Long deliveryBoyId, Pageable pageable);
}
