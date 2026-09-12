package com.zivdah.inventory.repository;

import com.zivdah.inventory.entity.Inventory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InventoryRepository extends ReactiveCrudRepository<Inventory, Long> {
    Mono<Inventory> findByProductId(Long productId);
    // Inventory has no createdAt (lastUpdated changes on every stock mutation, not just row
    // creation), so id DESC is the closest available proxy for "newest row first".
    Flux<Inventory> findAllByOrderByIdDesc(Pageable pageable);
}
