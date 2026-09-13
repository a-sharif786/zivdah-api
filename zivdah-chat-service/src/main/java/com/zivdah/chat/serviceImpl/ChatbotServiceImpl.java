package com.zivdah.chat.serviceImpl;

import com.zivdah.chat.chatbot.BotReply;
import com.zivdah.chat.chatbot.CancellableOrderStatuses;
import com.zivdah.chat.chatbot.ChatContext;
import com.zivdah.chat.chatbot.ChatbotProvider;
import com.zivdah.chat.client.OrderServiceClient;
import com.zivdah.chat.dto.BotConfirmRequestDto;
import com.zivdah.chat.dto.BotMessageRequestDto;
import com.zivdah.chat.dto.BotMessageResponseDto;
import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.entity.ChatMessage;
import com.zivdah.chat.enums.ConversationType;
import com.zivdah.chat.enums.MessageType;
import com.zivdah.chat.enums.SenderType;
import com.zivdah.chat.repository.MessageRepository;
import com.zivdah.chat.service.ChatbotService;
import com.zivdah.chat.service.ConversationService;
import com.zivdah.chat.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {

    private final ConversationService conversationService;
    // Shared persist path (also used by ChatWebSocketHandler and the REST fallback send
    // endpoint) — see MessageService's javadoc for why this exists as its own service rather
    // than each caller building/saving a ChatMessage itself.
    private final MessageService messageService;
    // Only ever read here for the bot's own last turn (see runBot) — every write still goes
    // through MessageService above.
    private final MessageRepository messageRepository;
    private final ChatbotProvider chatbotProvider;
    // Used only by confirmAction's real CANCEL_ORDER execution — RuleBasedChatbotProvider owns
    // the rest of the client calls (order/product/coupon/delivery) for the initial reply.
    private final OrderServiceClient orderServiceClient;

    @Override
    public Mono<BotMessageResponseDto> handleBotMessage(Long customerId, String bearerToken, BotMessageRequestDto request) {
        Mono<ChatConversation> conversationMono = request.getConversationId() == null
                ? conversationService.createConversation(customerId)
                // Never trusts the client-supplied conversationId without an ownership check —
                // 403 (ForbiddenOperationException) if it belongs to someone else.
                : conversationService.loadOwnedConversation(request.getConversationId(), customerId);

        return conversationMono
                .flatMap(conversation -> persistInboundMessage(conversation, customerId, request.getMessage())
                        .thenReturn(conversation))
                .flatMap(conversation -> {
                    // Already escalated to a human agent — skip the bot entirely, just let the
                    // frontend know to switch to the WebSocket channel.
                    if (conversation.getType() == ConversationType.HUMAN) {
                        return Mono.just(buildResponse(conversation, null));
                    }
                    return runBot(conversation, request.getMessage(), customerId, bearerToken);
                });
    }

    private Mono<BotMessageResponseDto> runBot(ChatConversation conversation, String message, Long customerId, String bearerToken) {
        // The bot's own last turn (if any) — lets RuleBasedChatbotProvider recognize a follow-up
        // reply (e.g. a bare order number) to its own previous question rather than treating
        // every message as a fresh, context-free one. A future LLM-backed provider can use the
        // same list for real multi-turn context with no other change here.
        return messageRepository.findFirstBySenderTypeAndConversationIdOrderByIdDesc(SenderType.BOT, conversation.getId())
                .map(List::of)
                .defaultIfEmpty(List.of())
                .flatMap(recentHistory -> {
                    ChatContext context = new ChatContext(
                            conversation.getId(), customerId, conversation.getOrderId(), bearerToken, recentHistory);
                    return chatbotProvider.reply(message, context);
                })
                .flatMap(botReply -> persistBotReplyMessage(conversation, botReply)
                        .then(botReply.requestHandoff()
                                ? conversationService.requestHumanHandoff(conversation)
                                : Mono.just(conversation))
                        .map(updatedConversation -> buildResponse(updatedConversation, botReply)));
    }

    private Mono<ChatMessage> persistInboundMessage(ChatConversation conversation, Long customerId, String message) {
        return messageService.persistMessage(conversation.getId(), customerId, SenderType.CUSTOMER,
                MessageType.TEXT, message, null);
    }

    private Mono<ChatMessage> persistBotReplyMessage(ChatConversation conversation, BotReply botReply) {
        return messageService.persistMessage(conversation.getId(), null, SenderType.BOT,
                MessageType.TEXT, botReply.text(), null);
    }

    private BotMessageResponseDto buildResponse(ChatConversation conversation, BotReply botReply) {
        return BotMessageResponseDto.builder()
                .conversationId(conversation.getId())
                .reply(botReply != null ? botReply.text() : null)
                .quickReplies(botReply != null ? botReply.quickReplies() : List.of())
                .conversationType(conversation.getType().name())
                .conversationStatus(conversation.getStatus().name())
                .handoffOffered(botReply != null && botReply.requestHandoff())
                .products(botReply != null ? botReply.products() : null)
                .orderCard(botReply != null ? botReply.orderCard() : null)
                .requiresConfirmation(botReply != null ? botReply.requiresConfirmation() : null)
                .build();
    }

    @Override
    public Mono<BotMessageResponseDto> confirmAction(Long customerId, String bearerToken, BotConfirmRequestDto request) {
        return conversationService.loadOwnedConversation(request.getConversationId(), customerId)
                .flatMap(conversation -> {
                    if (!"CANCEL_ORDER".equals(request.getActionType())) {
                        // No other actionType exists today — defensive fallback, shouldn't happen
                        // in practice since the frontend only ever echoes back what the bot gave it.
                        return replyAndPersist(conversation, "Nothing to confirm.");
                    }
                    if (!request.isConfirmed()) {
                        return replyAndPersist(conversation, "Order kept.");
                    }
                    Long orderId = extractOrderIdFromPayload(request.getPayload());
                    if (orderId == null) {
                        return replyAndPersist(conversation, "I couldn't find that order — please try again.");
                    }
                    // Never trusts the client-echoed payload blindly — always re-fetch and
                    // re-validate ownership/cancellable-state one more time before acting, since
                    // order-service itself enforces neither check on the cancel endpoint.
                    return orderServiceClient.getOrder(orderId)
                            .flatMap(order -> {
                                if (!order.getUserId().equals(customerId)) {
                                    return replyAndPersist(conversation, "I couldn't find that order on your account.");
                                }
                                if (!CancellableOrderStatuses.VALUES.contains(order.getStatus())) {
                                    return replyAndPersist(conversation, "That order can no longer be cancelled.");
                                }
                                return orderServiceClient.cancelOrder(orderId, bearerToken)
                                        .then(replyAndPersist(conversation,
                                                "Order #" + order.getOrderNumber() + " has been cancelled."));
                            })
                            .onErrorResume(ex -> {
                                log.warn("Cancel-order confirm flow failed for order {}: {}", orderId, ex.toString());
                                return replyAndPersist(conversation,
                                        "I'm having trouble cancelling that order right now — please try again "
                                                + "or talk to a human.");
                            });
                });
    }

    @SuppressWarnings("unchecked")
    private Long extractOrderIdFromPayload(Object payload) {
        if (payload instanceof Map<?, ?> map && map.get("orderId") != null) {
            try {
                return Long.valueOf(map.get("orderId").toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Mono<BotMessageResponseDto> replyAndPersist(ChatConversation conversation, String text) {
        return messageService.persistMessage(conversation.getId(), null, SenderType.BOT, MessageType.TEXT, text, null)
                .thenReturn(BotMessageResponseDto.builder()
                        .conversationId(conversation.getId())
                        .reply(text)
                        .quickReplies(List.of())
                        .conversationType(conversation.getType().name())
                        .conversationStatus(conversation.getStatus().name())
                        .handoffOffered(false)
                        .build());
    }
}
