package com.zivdah.inventory.kafka;

import com.zivdah.common.event.OrderCreatedEvent;
import com.zivdah.common.event.PaymentCompletedEvent;
import com.zivdah.common.event.ProductCreatedEvent;
import com.zivdah.inventory.entity.Inventory;
import com.zivdah.inventory.entity.InventoryReservation;
import com.zivdah.inventory.repository.InventoryRepository;
import com.zivdah.inventory.repository.InventoryReservationRepository;
import com.zivdah.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// Kafka listeners run on Kafka's blocking thread pool — .block() is safe here
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryService inventoryService;

    @KafkaListener(topics = "product-created", groupId = "inventory-group")
    public void onProductCreated(ProductCreatedEvent event) {
        int initialQuantity = event.getInitialStockQuantity() != null ? event.getInitialStockQuantity() : 0;
        // Reuses the existing upsert-safe addStock — safe to seed even if a row somehow
        // already exists (e.g. a redelivered message), it just adds on top rather than erroring.
        inventoryService.addStock(event.getProductId(), initialQuantity).block();
        log.info("Inventory seeded for new product {} with initial quantity {}", event.getProductId(), initialQuantity);
    }

    @KafkaListener(topics = "order-created", groupId = "inventory-group")
    public void onOrderCreated(OrderCreatedEvent event) {
        if (event.getItems() == null || event.getItems().isEmpty()) {
            log.warn("No items in OrderCreatedEvent for order {}", event.getOrderId());
            return;
        }
        log.info("Reserving inventory for order {}", event.getOrderId());
        for (var item : event.getItems()) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId()).block();
            if (inventory == null) {
                log.error("Inventory not found for product {}", item.getProductId());
                throw new RuntimeException("Inventory not found for product " + item.getProductId());
            }
            if (inventory.getAvailableQuantity() < item.getQuantity()) {
                log.error("Insufficient stock for product {} — available: {}, requested: {}",
                        item.getProductId(), inventory.getAvailableQuantity(), item.getQuantity());
                throw new RuntimeException("Insufficient stock for product " + item.getProductId());
            }
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - item.getQuantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() + item.getQuantity());
            inventory.setLastUpdated(LocalDateTime.now());
            inventoryRepository.save(inventory).block();

            InventoryReservation reservation = InventoryReservation.builder()
                    .orderId(event.getOrderId())
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .status("RESERVED")
                    .build();
            reservationRepository.save(reservation).block();
        }
        log.info("Inventory reserved for order {}", event.getOrderId());
    }

    @KafkaListener(topics = "payment-completed", groupId = "inventory-group")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        List<InventoryReservation> reservations =
                reservationRepository.findByOrderId(event.getOrderId()).collectList().block();
        if (reservations == null || reservations.isEmpty()) {
            log.warn("No inventory reservations found for order {}", event.getOrderId());
            return;
        }
        boolean paid = "PAID".equals(event.getStatus());
        log.info("Payment {} for order {} — {} inventory", event.getStatus(), event.getOrderId(),
                paid ? "confirming" : "releasing");
        for (InventoryReservation r : reservations) {
            Inventory inventory = inventoryRepository.findByProductId(r.getProductId()).block();
            if (inventory == null) continue;
            if (paid) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() - r.getQuantity());
                r.setStatus("CONFIRMED");
            } else {
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() + r.getQuantity());
                inventory.setReservedQuantity(inventory.getReservedQuantity() - r.getQuantity());
                r.setStatus("RELEASED");
            }
            inventory.setLastUpdated(LocalDateTime.now());
            inventoryRepository.save(inventory).block();
            reservationRepository.save(r).block();
        }
        log.info("Inventory {} for order {}", paid ? "confirmed" : "released", event.getOrderId());
    }
}
