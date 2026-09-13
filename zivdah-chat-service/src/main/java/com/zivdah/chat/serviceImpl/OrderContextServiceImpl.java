package com.zivdah.chat.serviceImpl;

import com.zivdah.chat.client.AuthServiceClient;
import com.zivdah.chat.client.DeliveryServiceClient;
import com.zivdah.chat.client.OrderServiceClient;
import com.zivdah.chat.client.PaymentServiceClient;
import com.zivdah.chat.client.dto.DeliverySummaryDto;
import com.zivdah.chat.client.dto.InternalUserInfoDto;
import com.zivdah.chat.client.dto.OrderSummaryDto;
import com.zivdah.chat.client.dto.PaymentSummaryDto;
import com.zivdah.chat.dto.OrderContextResponseDto;
import com.zivdah.chat.exception.ResourceNotFoundException;
import com.zivdah.chat.repository.ConversationRepository;
import com.zivdah.chat.service.OrderContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderContextServiceImpl implements OrderContextService {

    private final ConversationRepository conversationRepository;
    private final OrderServiceClient orderServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final DeliveryServiceClient deliveryServiceClient;
    private final AuthServiceClient authServiceClient;

    @Override
    public Mono<OrderContextResponseDto> getOrderContext(Long conversationId, String bearerToken) {
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Conversation not found: " + conversationId)))
                .flatMap(conversation -> {
                    Long orderId = conversation.getOrderId();
                    if (orderId == null) {
                        return Mono.error(new ResourceNotFoundException(
                                "Conversation " + conversationId + " has no linked order"));
                    }
                    return buildContext(orderId, bearerToken);
                });
    }

    // Each leg is wrapped so one dependency being down blanks only its own field(s), never the
    // whole panel (same defensive style used elsewhere for cross-service aggregation).
    private Mono<OrderContextResponseDto> buildContext(Long orderId, String bearerToken) {
        Mono<Optional<OrderSummaryDto>> orderMono = orderServiceClient.getOrder(orderId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(ex -> {
                    log.warn("order-context: failed to fetch order {}: {}", orderId, ex.toString());
                    return Mono.just(Optional.empty());
                });

        Mono<List<PaymentSummaryDto>> paymentsMono = paymentServiceClient.getPaymentsByOrder(orderId)
                .onErrorResume(ex -> {
                    log.warn("order-context: failed to fetch payments for order {}: {}", orderId, ex.toString());
                    return Mono.just(List.of());
                });

        Mono<List<DeliverySummaryDto>> deliveriesMono = deliveryServiceClient.getDeliveriesByOrder(orderId, bearerToken)
                .onErrorResume(ex -> {
                    log.warn("order-context: failed to fetch deliveries for order {}: {}", orderId, ex.toString());
                    return Mono.just(List.of());
                });

        return Mono.zip(orderMono, paymentsMono, deliveriesMono)
                .flatMap(tuple -> {
                    OrderSummaryDto order = tuple.getT1().orElse(null);
                    List<PaymentSummaryDto> payments = tuple.getT2();
                    List<DeliverySummaryDto> deliveries = tuple.getT3();
                    DeliverySummaryDto delivery = deliveries.isEmpty() ? null : deliveries.get(0);

                    return Mono.zip(
                            resolveName(delivery != null ? delivery.getVendorId() : null),
                            resolveName(delivery != null ? delivery.getDeliveryBoyId() : null)
                    ).map(names -> OrderContextResponseDto.builder()
                            .orderId(orderId)
                            .order(order)
                            .payments(payments)
                            .deliveries(deliveries)
                            .vendorName(names.getT1().orElse(null))
                            .deliveryBoyName(names.getT2().orElse(null))
                            .build());
                });
    }

    // Optional<String> wrapper: Mono can't itself emit null, but "no id to resolve" / "lookup
    // failed" both legitimately mean a null name in the response.
    private Mono<Optional<String>> resolveName(Long userId) {
        if (userId == null) {
            return Mono.just(Optional.empty());
        }
        return authServiceClient.getInternalUserInfo(userId)
                .map(InternalUserInfoDto::getName)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(ex -> {
                    log.warn("order-context: failed to resolve name for user {}: {}", userId, ex.toString());
                    return Mono.just(Optional.empty());
                });
    }
}
