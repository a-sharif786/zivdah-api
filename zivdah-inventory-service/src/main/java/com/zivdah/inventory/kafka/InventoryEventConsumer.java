package com.zivdah.inventory.kafka;

import com.zivdah.common.event.OrderCreatedEvent;
import com.zivdah.common.event.PaymentCompletedEvent;
import com.zivdah.inventory.entity.Inventory;
import com.zivdah.inventory.entity.InventoryReservation;
import com.zivdah.inventory.repository.InventoryRepository;
import com.zivdah.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    @SuppressWarnings("null")
    @KafkaListener(topics = "order-created", groupId = "inventory-group")
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        if (event.getItems() == null || event.getItems().isEmpty()) {
            log.warn("OrderCreatedEvent for order {} has no items, skipping inventory reserve", event.getOrderId());
            return;
        }
        log.info("Reserving inventory for order {}", event.getOrderId());
        for (var item : event.getItems()) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId())
                    .orElseThrow(() -> new RuntimeException(
                            "Inventory not found for product " + item.getProductId()));
            if (inventory.getAvailableQuantity() < item.getQuantity()) {
                log.error("Insufficient stock for product {} (order {}). Available: {}, requested: {}",
                        item.getProductId(), event.getOrderId(),
                        inventory.getAvailableQuantity(), item.getQuantity());
                throw new RuntimeException("Insufficient stock for product " + item.getProductId());
            }
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - item.getQuantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() + item.getQuantity());
            inventoryRepository.save(inventory);

            InventoryReservation reservation = InventoryReservation.builder()
                    .orderId(event.getOrderId())
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .status("RESERVED")
                    .build();
            reservationRepository.save(reservation);
        }
        log.info("Inventory reserved for order {}", event.getOrderId());
    }

    @KafkaListener(topics = "payment-completed", groupId = "inventory-group")
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(event.getOrderId());
        if (reservations.isEmpty()) {
            log.warn("No inventory reservations found for order {}", event.getOrderId());
            return;
        }
        boolean paid = "PAID".equals(event.getStatus());
        log.info("Payment {} for order {} — {} inventory", event.getStatus(), event.getOrderId(),
                paid ? "confirming" : "releasing");

        for (InventoryReservation r : reservations) {
            Inventory inventory = inventoryRepository.findByProductId(r.getProductId())
                    .orElseThrow(() -> new RuntimeException(
                            "Inventory not found for product " + r.getProductId()));
            if (paid) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() - r.getQuantity());
                r.setStatus("CONFIRMED");
            } else {
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() + r.getQuantity());
                inventory.setReservedQuantity(inventory.getReservedQuantity() - r.getQuantity());
                r.setStatus("RELEASED");
            }
            inventoryRepository.save(inventory);
            reservationRepository.save(r);
        }
        log.info("Inventory {} for order {}", paid ? "confirmed" : "released", event.getOrderId());
    }
}
