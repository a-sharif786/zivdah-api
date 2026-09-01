package com.zivdah.delivery.repository;

import com.zivdah.delivery.entity.Delivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeliveryRepository extends ReactiveCrudRepository<Delivery, Long> {
    Mono<Delivery> findByOrderIdAndVendorId(Long orderId, Long vendorId);
    Flux<Delivery> findByOrderId(Long orderId);
    Flux<Delivery> findByVendorId(Long vendorId, Pageable pageable);
    Flux<Delivery> findByDeliveryBoyId(Long deliveryBoyId, Pageable pageable);
}
