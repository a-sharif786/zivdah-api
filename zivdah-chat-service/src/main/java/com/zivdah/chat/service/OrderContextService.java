package com.zivdah.chat.service;

import com.zivdah.chat.dto.OrderContextResponseDto;
import reactor.core.publisher.Mono;

public interface OrderContextService {

    // GET /conversations/{id}/order-context — ResourceNotFoundException (404) if the conversation
    // itself doesn't exist, or if it exists but has no linked order. bearerToken is the calling
    // ADMIN's own JWT, forwarded to delivery-service's call (which requires an authenticated
    // caller and does its own server-side visibility filtering).
    Mono<OrderContextResponseDto> getOrderContext(Long conversationId, String bearerToken);
}
