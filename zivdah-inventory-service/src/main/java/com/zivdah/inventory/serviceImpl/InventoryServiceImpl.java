package com.zivdah.inventory.serviceImpl;

import com.zivdah.inventory.dto.InventoryResponseDto;
import com.zivdah.inventory.entity.Inventory;
import com.zivdah.inventory.repository.InventoryRepository;
import com.zivdah.inventory.service.InventoryService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    @Override
    public InventoryResponseDto getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found"));
        return mapToDto(inventory);
    }

    @Override
    public InventoryResponseDto addStock(Long productId, Integer quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElse(Inventory.builder()
                        .productId(productId)
                        .availableQuantity(0)
                        .reservedQuantity(0)
                        .build());

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        inventoryRepository.save(inventory);

        log.info("Stock added for product {}", productId);
        return mapToDto(inventory);
    }

    @Override
    public InventoryResponseDto reserveStock(Long productId, Integer quantity) {    //store or reserve product
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found"));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new RuntimeException("Out of stock");
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);

        inventoryRepository.save(inventory);
        log.info("Stock reserved for product {}", productId);

        return mapToDto(inventory);
    }

    @Override
    public InventoryResponseDto releaseStock(Long productId, Integer quantity) {   //fail payment
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found"));


        if (inventory.getReservedQuantity() < quantity) {
            throw new RuntimeException("Not enough reserved stock to release");
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);

        inventoryRepository.save(inventory);
        log.info("Stock released for product {}", productId);

        return mapToDto(inventory);
    }

    @Override
    public InventoryResponseDto confirmStock(Long productId, Integer quantity) {  //success payment
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found"));

        if (inventory.getReservedQuantity() < quantity) {
            throw new RuntimeException(
                    "Not enough reserved stock to confirm. Reserved: "
                            + inventory.getReservedQuantity()
            );
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventoryRepository.save(inventory);

        log.info("Stock confirmed for product {}", productId);
        return mapToDto(inventory);
    }

    private InventoryResponseDto mapToDto(Inventory inventory) {
        return InventoryResponseDto.builder()
                .productId(inventory.getProductId())
                .availableQuantity(inventory.getAvailableQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .lastUpdated(inventory.getLastUpdated())
                .build();
    }
}
