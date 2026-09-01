package com.zivdah.delivery.serviceImpl;

import com.zivdah.common.event.DeliveryAssignedEvent;
import com.zivdah.common.event.DeliveryCompletedEvent;
import com.zivdah.common.event.DeliveryFailedEvent;
import com.zivdah.common.event.OrderStatusChangedEvent;
import com.zivdah.delivery.client.OrderServiceClient;
import com.zivdah.delivery.dto.DeliveryResponseDto;
import com.zivdah.delivery.entity.Delivery;
import com.zivdah.delivery.enums.DeliveryStatus;
import com.zivdah.delivery.enums.FailureReason;
import com.zivdah.delivery.kafka.DeliveryKafkaProducer;
import com.zivdah.delivery.repository.DeliveryRepository;
import com.zivdah.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.zivdah.delivery.enums.DeliveryStatus.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderServiceClient orderServiceClient;
    private final DeliveryKafkaProducer deliveryKafkaProducer;

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(DeliveryStatus.class);

    // Vendor/Admin drive packing, before a delivery boy is even involved, and may cancel up
    // to the point of pickup. Delivery Boy drives everything from pickup onward.
    private static final Set<DeliveryStatus> VENDOR_ADMIN_TARGETS = EnumSet.of(PACKED, READY_FOR_PICKUP, CANCELLED);
    private static final Set<DeliveryStatus> DELIVERY_BOY_TARGETS = EnumSet.of(PICKED_UP, ON_THE_WAY, DELIVERED, FAILED);

    static {
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(PACKED, CANCELLED));
        ALLOWED_TRANSITIONS.put(PACKED, EnumSet.of(READY_FOR_PICKUP, CANCELLED));
        ALLOWED_TRANSITIONS.put(READY_FOR_PICKUP, EnumSet.of(PICKED_UP, CANCELLED));
        ALLOWED_TRANSITIONS.put(PICKED_UP, EnumSet.of(ON_THE_WAY));
        ALLOWED_TRANSITIONS.put(ON_THE_WAY, EnumSet.of(DELIVERED, FAILED));
        ALLOWED_TRANSITIONS.put(DELIVERED, EnumSet.noneOf(DeliveryStatus.class));
        ALLOWED_TRANSITIONS.put(FAILED, EnumSet.noneOf(DeliveryStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(DeliveryStatus.class));
    }

    @Override
    public Mono<Void> createPendingDeliveriesForOrder(Long orderId, Long userId) {
        return orderServiceClient.getVendorIds(orderId)
                .flatMapMany(Flux::fromIterable)
                .flatMap(vendorId -> deliveryRepository.findByOrderIdAndVendorId(orderId, vendorId)
                        .switchIfEmpty(Mono.defer(() -> {
                            LocalDateTime now = LocalDateTime.now();
                            return deliveryRepository.save(Delivery.builder()
                                    .orderId(orderId)
                                    .vendorId(vendorId)
                                    .userId(userId)
                                    .status(PENDING)
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build());
                        })))
                .then();
    }

    @Override
    public Mono<DeliveryResponseDto> assignDeliveryBoy(Long deliveryId, Long deliveryBoyId, Long currentUserId, String role) {
        if (!"ADMIN".equalsIgnoreCase(role) && !"VENDOR".equalsIgnoreCase(role)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN or VENDOR may assign a delivery boy"));
        }
        return deliveryRepository.findById(deliveryId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found")))
                .flatMap(delivery -> {
                    if ("VENDOR".equalsIgnoreCase(role) && !currentUserId.equals(delivery.getVendorId())) {
                        return Mono.<Delivery>error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this delivery"));
                    }
                    delivery.setDeliveryBoyId(deliveryBoyId);
                    delivery.setAssignedAt(LocalDateTime.now());
                    delivery.setUpdatedAt(LocalDateTime.now());
                    return deliveryRepository.save(delivery);
                })
                .doOnSuccess(saved -> deliveryKafkaProducer.publishDeliveryAssigned(
                        DeliveryAssignedEvent.builder()
                                .deliveryId(saved.getId())
                                .orderId(saved.getOrderId())
                                .vendorId(saved.getVendorId())
                                .userId(saved.getUserId())
                                .deliveryBoyId(saved.getDeliveryBoyId())
                                .assignedByUserId(currentUserId)
                                .assignedByRole(role)
                                .build()))
                .map(this::mapToDto);
    }

    @Override
    public Mono<DeliveryResponseDto> updateStatus(Long deliveryId, DeliveryStatus newStatus,
                                                    FailureReason failureReason, String failureNote,
                                                    Long currentUserId, String role) {
        if (newStatus == FAILED && failureReason == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "failureReason is required when status is FAILED"));
        }

        return deliveryRepository.findById(deliveryId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found")))
                .flatMap(delivery -> authorizeTransition(delivery, newStatus, currentUserId, role)
                        .then(Mono.defer(() -> {
                            Set<DeliveryStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(delivery.getStatus(), EnumSet.noneOf(DeliveryStatus.class));
                            if (!allowed.contains(newStatus)) {
                                return Mono.<Delivery>error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Cannot transition delivery from " + delivery.getStatus() + " to " + newStatus));
                            }
                            delivery.setStatus(newStatus);
                            delivery.setUpdatedAt(LocalDateTime.now());
                            if (newStatus == FAILED) {
                                delivery.setFailureReason(failureReason);
                                delivery.setFailureNote(failureNote);
                            }
                            return deliveryRepository.save(delivery)
                                    .flatMap(saved -> publishForStatus(saved, newStatus, currentUserId, role).thenReturn(saved));
                        })))
                .map(this::mapToDto);
    }

    private Mono<Void> authorizeTransition(Delivery delivery, DeliveryStatus newStatus, Long currentUserId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return Mono.empty();
        }
        if ("VENDOR".equalsIgnoreCase(role)) {
            if (!VENDOR_ADMIN_TARGETS.contains(newStatus)) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Vendors may not set status " + newStatus));
            }
            if (!currentUserId.equals(delivery.getVendorId())) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this delivery"));
            }
            return Mono.empty();
        }
        if ("DELIVERY_BOY".equalsIgnoreCase(role)) {
            if (!DELIVERY_BOY_TARGETS.contains(newStatus)) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Delivery boys may not set status " + newStatus));
            }
            if (!currentUserId.equals(delivery.getDeliveryBoyId())) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not assigned to this delivery"));
            }
            return Mono.empty();
        }
        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to update delivery status"));
    }

    // Fans a saved transition out to the right Kafka topic. DELIVERED and FAILED are
    // significant enough to get their own topics so notification-service can react
    // differently (delivery-completed vs. delivery-failed, the latter carrying a reason);
    // PACKED/READY_FOR_PICKUP/PICKED_UP/ON_THE_WAY reuse order-status-changed — the same
    // topic/event shape order-service's own order-level transitions already publish on (see
    // OrderStatusChangedEvent's vendorId/deliveryBoyId). CANCELLED publishes on that same
    // topic too but as "DELIVERY_CANCELLED", not the enum's own "CANCELLED" — order-service
    // already publishes an order-level CANCELLED on this exact topic with different
    // semantics (a rejected/cancelled *order*, not a delivery still awaiting pickup), and
    // notification-service's consumer needs to tell the two apart.
    private Mono<Void> publishForStatus(Delivery saved, DeliveryStatus newStatus, Long currentUserId, String role) {
        return switch (newStatus) {
            case DELIVERED -> {
                deliveryKafkaProducer.publishDeliveryCompleted(DeliveryCompletedEvent.builder()
                        .deliveryId(saved.getId())
                        .orderId(saved.getOrderId())
                        .vendorId(saved.getVendorId())
                        .userId(saved.getUserId())
                        .deliveryBoyId(saved.getDeliveryBoyId())
                        .build());
                // Only sync the parent Order to DELIVERED once every vendor-portion of it is
                // — a multi-vendor order isn't fully delivered just because one vendor's is.
                yield deliveryRepository.findByOrderId(saved.getOrderId())
                        .collectList()
                        .flatMap(all -> all.stream().allMatch(d -> d.getStatus() == DELIVERED)
                                ? orderServiceClient.syncOrderDeliveryStatus(saved.getOrderId(), "DELIVERED")
                                : Mono.empty());
            }
            case FAILED -> {
                deliveryKafkaProducer.publishDeliveryFailed(DeliveryFailedEvent.builder()
                        .deliveryId(saved.getId())
                        .orderId(saved.getOrderId())
                        .vendorId(saved.getVendorId())
                        .userId(saved.getUserId())
                        .deliveryBoyId(saved.getDeliveryBoyId())
                        .failureReason(saved.getFailureReason() == null ? null : saved.getFailureReason().name())
                        .failureNote(saved.getFailureNote())
                        .build());
                yield Mono.empty();
            }
            default -> {
                String publishedStatus = newStatus == CANCELLED ? "DELIVERY_CANCELLED" : newStatus.name();
                deliveryKafkaProducer.publishOrderStatusChanged(OrderStatusChangedEvent.builder()
                        .orderId(saved.getOrderId())
                        .userId(saved.getUserId())
                        .newStatus(publishedStatus)
                        .changedByUserId(currentUserId)
                        .changedByRole(role)
                        .vendorId(saved.getVendorId())
                        .deliveryBoyId(saved.getDeliveryBoyId())
                        .build());
                yield newStatus == ON_THE_WAY
                        ? orderServiceClient.syncOrderDeliveryStatus(saved.getOrderId(), "ON_THE_WAY")
                        : Mono.empty();
            }
        };
    }

    @Override
    public Flux<DeliveryResponseDto> getByOrder(Long orderId, Long currentUserId, String role) {
        return deliveryRepository.findByOrderId(orderId)
                .filter(d -> isVisibleTo(d, currentUserId, role))
                .map(this::mapToDto);
    }

    private boolean isVisibleTo(Delivery d, Long currentUserId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) return true;
        if ("VENDOR".equalsIgnoreCase(role)) return currentUserId.equals(d.getVendorId());
        if ("DELIVERY_BOY".equalsIgnoreCase(role)) return currentUserId.equals(d.getDeliveryBoyId());
        return currentUserId.equals(d.getUserId());
    }

    @Override
    public Flux<DeliveryResponseDto> getByVendor(Long vendorId, Pageable pageable, Long currentUserId, String role) {
        if ("VENDOR".equalsIgnoreCase(role) && !currentUserId.equals(vendorId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Vendors may only query their own deliveries"));
        }
        return deliveryRepository.findByVendorId(vendorId, pageable).map(this::mapToDto);
    }

    @Override
    public Flux<DeliveryResponseDto> getMyDeliveries(Long deliveryBoyId, Pageable pageable) {
        return deliveryRepository.findByDeliveryBoyId(deliveryBoyId, pageable).map(this::mapToDto);
    }

    private DeliveryResponseDto mapToDto(Delivery d) {
        return DeliveryResponseDto.builder()
                .id(d.getId())
                .orderId(d.getOrderId())
                .vendorId(d.getVendorId())
                .userId(d.getUserId())
                .deliveryBoyId(d.getDeliveryBoyId())
                .status(d.getStatus())
                .failureReason(d.getFailureReason())
                .failureNote(d.getFailureNote())
                .assignedAt(d.getAssignedAt())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
