package com.zivdah.inventory.repository;

import com.zivdah.inventory.entity.Inventory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InventoryRepository extends ReactiveCrudRepository<Inventory, Long> {
    Mono<Inventory> findByProductId(Long productId);
    Flux<Inventory> findAllBy(Pageable pageable);
}
