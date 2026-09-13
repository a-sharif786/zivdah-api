package com.zivdah.chat.chatbot;

import com.zivdah.chat.client.CouponServiceClient;
import com.zivdah.chat.client.DeliveryServiceClient;
import com.zivdah.chat.client.OrderServiceClient;
import com.zivdah.chat.client.ProductServiceClient;
import com.zivdah.chat.client.dto.OrderSummaryDto;
import com.zivdah.chat.client.dto.ProductSummaryDto;
import com.zivdah.chat.dto.ConfirmationPromptDto;
import com.zivdah.chat.entity.ChatMessage;
import com.zivdah.chat.enums.SenderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Demo/free-tier implementation — keyword/intent matching only, no paid API key. Swap in a real
 * LLM by implementing {@link ChatbotProvider} and marking it {@code @Primary} (same pattern as
 * the OTP placeholder — a static "123456" today, a real SMS/email provider later).
 *
 * <p>Phase 4: every intent that needs a real Zivdah service call is now wired to the matching
 * {@code client/*ServiceClient} — never inventing order/product/payment/delivery data. Every
 * client call is wrapped so a failure/timeout falls back to the plan's exact section-16 copy
 * ("I'm having trouble answering right now...") rather than surfacing a raw error or guessing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RuleBasedChatbotProvider implements ChatbotProvider {

    private final IntentClassifier intentClassifier;
    private final OrderServiceClient orderServiceClient;
    private final ProductServiceClient productServiceClient;
    private final CouponServiceClient couponServiceClient;
    private final DeliveryServiceClient deliveryServiceClient;

    private static final String FALLBACK_QUICK_REPLY = "Talk to Human";

    // Exact copy per the plan's section 16.
    private static final String FALLBACK_TEXT =
            "I'm having trouble answering right now.\nWould you like to talk to a human support agent?";

    private static final Pattern ORDER_ID_PATTERN =
            Pattern.compile("(?:zvd|order\\s*#?|#)\\s*(\\d{2,})|\\b(\\d{3,})\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRICE_CEILING_PATTERN = Pattern.compile(
            "\\b(?:under|below|less than|up to)\\s*(?:aed|rs\\.?|inr|\\$)?\\s*(\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ORGANIC_PATTERN = Pattern.compile("\\borganic\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern SEARCH_STOPWORDS_PATTERN = Pattern.compile(
            "\\b(looking for|search for|do you have|show me|find me|any|available|organic|under.*|below.*|less than.*|up to.*)\\b",
            Pattern.CASE_INSENSITIVE);

    // Maps common plurals/synonyms a customer would actually type onto the canonical category
    // name product-service expects — e.g. "veggies" never matched "vegetable" before, silently
    // falling back to a plain keyword search that often returned nothing.
    private static final Map<String, String> CATEGORY_SYNONYMS = Map.ofEntries(
            Map.entry("vegetable", "vegetable"), Map.entry("vegetables", "vegetable"),
            Map.entry("veggie", "vegetable"), Map.entry("veggies", "vegetable"),
            Map.entry("fruit", "fruit"), Map.entry("fruits", "fruit"),
            Map.entry("milk", "milk"), Map.entry("dairy", "milk"),
            Map.entry("pulse", "pulse"), Map.entry("pulses", "pulse"),
            Map.entry("lentil", "pulse"), Map.entry("lentils", "pulse"), Map.entry("dal", "pulse"),
            Map.entry("grocery", "grocery"), Map.entry("groceries", "grocery"));

    // Exact text of the two "which order?" follow-up prompts below — also used to recognize,
    // via context.recentHistory(), that the customer's *next* message (e.g. a bare "1042") is
    // answering one of these rather than a fresh, unrelated message the classifier should judge
    // on its own. Package-private so ChatbotServiceImplTest can assert against them directly.
    static final String ASK_ORDER_ID_FOR_STATUS =
            "Which order number would you like me to check? For example: \"where is order 1042\".";
    static final String ASK_ORDER_ID_FOR_CANCEL =
            "Which order would you like to cancel? Please share the order number.";

    @Override
    public Mono<BotReply> reply(String userMessage, ChatContext context) {
        IntentClassifier.Intent intent = intentClassifier.classify(userMessage);

        // Multi-turn slot-filling: only kicks in when the classifier found nothing else to go
        // on, and only continues the SAME flow the bot itself just asked about — a message that
        // matches some other real intent (e.g. "cancel", "talk to human") always takes priority.
        if (intent == IntentClassifier.Intent.UNKNOWN) {
            String lastBotText = lastBotMessageText(context);
            if (ASK_ORDER_ID_FOR_CANCEL.equals(lastBotText)) {
                return handleCancelOrder(userMessage, context);
            }
            if (ASK_ORDER_ID_FOR_STATUS.equals(lastBotText)) {
                return handleOrderStatus(userMessage, context);
            }
        }

        return switch (intent) {
            case TALK_TO_HUMAN -> Mono.just(new BotReply(
                    "Sure — connecting you with a human support agent now.",
                    List.of(),
                    true,
                    "GENERAL"));

            case REFUND_REQUEST -> Mono.just(new BotReply(
                    // No self-service refund endpoint exists (refund is ADMIN-only) — never call
                    // payment-service directly, always hand off to a human agent.
                    "I've forwarded your refund request to our support team — a human agent will "
                            + "take it from here.",
                    List.of(),
                    true,
                    "REFUND"));

            case ORDER_STATUS -> handleOrderStatus(userMessage, context);

            case CANCEL_ORDER -> handleCancelOrder(userMessage, context);

            case ORDER_HISTORY -> handleOrderHistory(context);

            case PRODUCT_SEARCH -> handleProductSearch(userMessage);

            case OFFERS -> handleOffers();

            case DELIVERY_TIME -> handleDeliveryTime(context);

            // Fully implemented — static, honest answer; no address-edit endpoint exists.
            case CHANGE_ADDRESS -> Mono.just(new BotReply(
                    "You can add a new delivery address and mark it default — editing an existing "
                            + "saved address isn't supported yet.",
                    List.of(),
                    false,
                    "ACCOUNT"));

            default -> Mono.just(new BotReply(
                    "I'm not sure I understood that. I can help with order status, cancellations, "
                            + "product search, offers, delivery times, or address changes — or I can "
                            + "connect you with a human.",
                    List.of(FALLBACK_QUICK_REPLY),
                    false,
                    "GENERAL"));
        };
    }

    private Mono<BotReply> handleOrderStatus(String message, ChatContext context) {
        Long orderId = extractOrderId(message, context.orderId());
        if (orderId == null) {
            return Mono.just(new BotReply(ASK_ORDER_ID_FOR_STATUS, List.of(), false, "ORDER_STATUS"));
        }
        return orderServiceClient.getOrder(orderId)
                .map(order -> withOwnershipCheck(order, context.customerId(), owned -> new BotReply(
                        "Order #" + owned.getOrderNumber() + " is currently " + owned.getStatus()
                                + ". Total: " + owned.getCurrency() + " " + owned.getTotalAmount() + ".",
                        List.of(), false, "ORDER_STATUS", null, owned, null)))
                .onErrorResume(ex -> fallback("ORDER_STATUS", ex));
    }

    private Mono<BotReply> handleCancelOrder(String message, ChatContext context) {
        Long orderId = extractOrderId(message, context.orderId());
        if (orderId == null) {
            return Mono.just(new BotReply(ASK_ORDER_ID_FOR_CANCEL, List.of(), false, "ORDER_STATUS"));
        }
        return orderServiceClient.getOrder(orderId)
                .map(order -> {
                    // order-service enforces NO ownership check here — verify before ever acting.
                    if (!order.getUserId().equals(context.customerId())) {
                        return new BotReply(
                                "I couldn't find that order on your account.", List.of(), false, "ORDER_STATUS");
                    }
                    // order-service also enforces NO allowed-transition check on the cancel
                    // endpoint itself — verify the current status is still cancellable before
                    // ever offering to cancel it.
                    if (!CancellableOrderStatuses.VALUES.contains(order.getStatus())) {
                        return new BotReply(
                                "Order #" + order.getOrderNumber() + " is already " + order.getStatus()
                                        + " and can no longer be cancelled here. Would you like to talk "
                                        + "to a human about it?",
                                List.of(FALLBACK_QUICK_REPLY), false, "ORDER_STATUS");
                    }
                    ConfirmationPromptDto confirmation = ConfirmationPromptDto.builder()
                            .actionType("CANCEL_ORDER")
                            .payload(Map.of("orderId", order.getOrderId()))
                            .prompt("Are you sure you want to cancel Order #" + order.getOrderNumber() + "?")
                            .build();
                    // The actual cancel call only ever happens via POST /bot/confirm, after the
                    // customer explicitly confirms — never on this initial turn.
                    return new BotReply(
                            confirmation.getPrompt(), List.of("Cancel Order", "Keep Order"), false,
                            "ORDER_STATUS", null, null, confirmation);
                })
                .onErrorResume(ex -> fallback("ORDER_STATUS", ex));
    }

    private Mono<BotReply> handleOrderHistory(ChatContext context) {
        // Always the JWT-derived customerId from context — never anything parsed from the message.
        return orderServiceClient.getOrdersByUser(context.customerId(), context.bearerToken())
                .map(orders -> {
                    if (orders.isEmpty()) {
                        return new BotReply("You don't have any orders yet.", List.of(), false, "ORDER_STATUS");
                    }
                    String summary = orders.stream()
                            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                            .limit(5)
                            .map(o -> "#" + o.getOrderNumber() + " — " + o.getStatus()
                                    + " (" + o.getCurrency() + " " + o.getTotalAmount() + ")")
                            .collect(Collectors.joining("\n"));
                    return new BotReply("Here are your most recent orders:\n" + summary,
                            List.of(), false, "ORDER_STATUS");
                })
                .onErrorResume(ex -> fallback("ORDER_STATUS", ex));
    }

    private Mono<BotReply> handleProductSearch(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        BigDecimal priceCeiling = extractPriceCeiling(lower);
        boolean organicOnly = ORGANIC_PATTERN.matcher(lower).find();
        String category = detectCategory(lower);

        Mono<List<ProductSummaryDto>> resultsMono = category != null
                ? productServiceClient.searchByCategory(category, 0, 20)
                : productServiceClient.searchByKeyword(extractKeyword(lower), 0, 20);

        return resultsMono
                .map(products -> {
                    // No server-side price-range/organic filter exists — filter in-memory.
                    List<ProductSummaryDto> filtered = products.stream()
                            .filter(p -> priceCeiling == null || effectivePrice(p).compareTo(priceCeiling) <= 0)
                            .filter(p -> !organicOnly || Boolean.TRUE.equals(p.getOrganic()))
                            .limit(5)
                            .toList();
                    if (filtered.isEmpty()) {
                        return new BotReply(
                                "I couldn't find any products matching that — try a different search.",
                                List.of(), false, "PRODUCT");
                    }
                    return new BotReply("Here's what I found:", List.of(), false, "PRODUCT", filtered, null, null);
                })
                .onErrorResume(ex -> fallback("PRODUCT", ex));
    }

    private Mono<BotReply> handleOffers() {
        return couponServiceClient.getActiveOffers()
                .map(offers -> {
                    if (offers.isEmpty()) {
                        return new BotReply(
                                "There are no active offers right now — check back soon!", List.of(), false, "GENERAL");
                    }
                    String summary = offers.stream()
                            .map(c -> c.getCode() + " — " + c.getDescription())
                            .collect(Collectors.joining("\n"));
                    return new BotReply("Here are our current offers:\n" + summary, List.of(), false, "GENERAL");
                })
                .onErrorResume(ex -> fallback("GENERAL", ex));
    }

    private Mono<BotReply> handleDeliveryTime(ChatContext context) {
        if (context.orderId() == null) {
            return Mono.just(new BotReply(
                    "Most orders are delivered within 24-48 hours of confirmation. Share an order "
                            + "number if you'd like a specific update.",
                    List.of(), false, "DELIVERY"));
        }
        return deliveryServiceClient.getDeliveriesByOrder(context.orderId(), context.bearerToken())
                .map(deliveries -> deliveries.isEmpty()
                        ? new BotReply("I don't have delivery details for that order yet.", List.of(), false, "DELIVERY")
                        : new BotReply("Your delivery is currently: " + deliveries.get(0).getStatus() + ".",
                                List.of(), false, "DELIVERY"))
                .onErrorResume(ex -> fallback("DELIVERY", ex));
    }

    // The most recent BOT-authored message text, if ChatbotServiceImpl populated
    // context.recentHistory() with one (it's empty on a conversation's very first turn, or if
    // the last message somehow wasn't from the bot) — null otherwise.
    private String lastBotMessageText(ChatContext context) {
        return context.recentHistory().stream()
                .filter(m -> m.getSenderType() == SenderType.BOT)
                .map(ChatMessage::getMessage)
                .findFirst()
                .orElse(null);
    }

    private BotReply withOwnershipCheck(OrderSummaryDto order, Long customerId, Function<OrderSummaryDto, BotReply> onOwned) {
        if (!order.getUserId().equals(customerId)) {
            return new BotReply("I couldn't find that order on your account.", List.of(), false, "ORDER_STATUS");
        }
        return onOwned.apply(order);
    }

    private Mono<BotReply> fallback(String topic, Throwable ex) {
        log.warn("Chatbot client call failed for topic {}: {}", topic, ex.toString());
        return Mono.just(new BotReply(FALLBACK_TEXT, List.of(FALLBACK_QUICK_REPLY), false, topic));
    }

    private Long extractOrderId(String message, Long contextOrderId) {
        if (contextOrderId != null) {
            return contextOrderId;
        }
        Matcher m = ORDER_ID_PATTERN.matcher(message);
        if (m.find()) {
            String g = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                return Long.valueOf(g);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal extractPriceCeiling(String lower) {
        Matcher m = PRICE_CEILING_PATTERN.matcher(lower);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private String detectCategory(String lower) {
        for (Map.Entry<String, String> synonym : CATEGORY_SYNONYMS.entrySet()) {
            if (lower.contains(synonym.getKey())) {
                return synonym.getValue().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private String extractKeyword(String lower) {
        String stripped = SEARCH_STOPWORDS_PATTERN.matcher(lower).replaceAll(" ").trim();
        return stripped.isBlank() ? lower.trim() : stripped;
    }

    private BigDecimal effectivePrice(ProductSummaryDto p) {
        return p.getDiscountPrice() != null ? p.getDiscountPrice() : p.getPrice();
    }
}
