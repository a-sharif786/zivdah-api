package com.zivdah.inventory.serviceImpl;

import com.zivdah.inventory.dto.InventoryResponseDto;
import com.zivdah.inventory.entity.Inventory;
import com.zivdah.inventory.repository.InventoryRepository;
import com.zivdah.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    @Override
    public Mono<InventoryResponseDto> getInventoryByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found for product: " + productId)))
                .map(this::mapToDto);
    }

    @Override
    public Mono<InventoryResponseDto> addStock(Long productId, Integer quantity) {
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.just(Inventory.builder()
                        .productId(productId)
                        .availableQuantity(0)
                        .reservedQuantity(0)
                        .build()))
                .flatMap(inv -> {
                    inv.setAvailableQuantity(inv.getAvailableQuantity() + quantity);
                    inv.setLastUpdated(LocalDateTime.now());
                    return inventoryRepository.save(inv);
                })
                .doOnSuccess(inv -> log.info("Stock added for product {}", productId))
                .map(this::mapToDto);
    }

    @Override
    public Mono<InventoryResponseDto> reserveStock(Long productId, Integer quantity) {
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found for product: " + productId)))
                .flatMap(inv -> {
                    if (inv.getAvailableQuantity() < quantity) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Out of stock for product: " + productId));
                    }
                    inv.setAvailableQuantity(inv.getAvailableQuantity() - quantity);
                    inv.setReservedQuantity(inv.getReservedQuantity() + quantity);
                    inv.setLastUpdated(LocalDateTime.now());
                    return inventoryRepository.save(inv);
                })
                .doOnSuccess(inv -> log.info("Stock reserved for product {}", productId))
                .map(this::mapToDto);
    }

    @Override
    public Mono<InventoryResponseDto> releaseStock(Long productId, Integer quantity) {
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found for product: " + productId)))
                .flatMap(inv -> {
                    if (inv.getReservedQuantity() < quantity) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Not enough reserved stock"));
                    }
                    inv.setAvailableQuantity(inv.getAvailableQuantity() + quantity);
                    inv.setReservedQuantity(inv.getReservedQuantity() - quantity);
                    inv.setLastUpdated(LocalDateTime.now());
                    return inventoryRepository.save(inv);
                })
                .doOnSuccess(inv -> log.info("Stock released for product {}", productId))
                .map(this::mapToDto);
    }

    @Override
    public Mono<InventoryResponseDto> confirmStock(Long productId, Integer quantity) {
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found for product: " + productId)))
                .flatMap(inv -> {
                    if (inv.getReservedQuantity() < quantity) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Not enough reserved stock to confirm"));
                    }
                    inv.setReservedQuantity(inv.getReservedQuantity() - quantity);
                    inv.setLastUpdated(LocalDateTime.now());
                    return inventoryRepository.save(inv);
                })
                .doOnSuccess(inv -> log.info("Stock confirmed for product {}", productId))
                .map(this::mapToDto);
    }

    @Override
    public Flux<InventoryResponseDto> getAllInventory(Pageable pageable) {
        return inventoryRepository.findAllBy(pageable).map(this::mapToDto);
    }

    private InventoryResponseDto mapToDto(Inventory inv) {
        return InventoryResponseDto.builder()
                .productId(inv.getProductId())
                .availableQuantity(inv.getAvailableQuantity())
                .reservedQuantity(inv.getReservedQuantity())
                .lastUpdated(inv.getLastUpdated())
                .build();
    }
}
