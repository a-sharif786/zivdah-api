package com.zivdah.common.constants;

public class KafkaTopics {
    public static final String ORDER_CREATED = "order-created";
    public static final String PAYMENT_COMPLETED = "payment-completed";
    public static final String PRODUCT_CREATED = "product-created";
    public static final String ORDER_STATUS_CHANGED = "order-status-changed";
    public static final String DELIVERY_ASSIGNED = "delivery-assigned";
    public static final String DELIVERY_FAILED = "delivery-failed";
    public static final String DELIVERY_COMPLETED = "delivery-completed";
    public static final String APP_LOGS = "app-logs";
    public static final String CHAT_HUMAN_REQUESTED = "chat-human-requested";
    public static final String CHAT_MESSAGE_SENT = "chat-message-sent";
    public static final String CHAT_CONVERSATION_ACCEPTED = "chat-conversation-accepted";
    public static final String CHAT_CONVERSATION_CLOSED = "chat-conversation-closed";
    private KafkaTopics() {}
}
