package com.zivdah.inventory.repository;

import com.zivdah.inventory.entity.InventoryReservation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface InventoryReservationRepository extends ReactiveCrudRepository<InventoryReservation, Long> {
    Flux<InventoryReservation> findByOrderId(Long orderId);
}
