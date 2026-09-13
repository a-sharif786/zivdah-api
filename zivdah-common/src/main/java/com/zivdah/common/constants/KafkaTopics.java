package com.zivdah.common.constants;

public class KafkaTopics {
    public static final String ORDER_CREATED = "order-created";
    public static final String PAYMENT_COMPLETED = "payment-completed";
    public static final String PRODUCT_CREATED = "product-created";
    public static final String ORDER_STATUS_CHANGED = "order-status-changed";
    public static final String DELIVERY_ASSIGNED = "delivery-assigned";
    public static final String DELIVERY_FAILED = "delivery-failed";
    public static final String DELIVERY_COMPLETED = "delivery-completed";

    // Log events shipped by com.zivdah.common.logging.KafkaLogAppender from every service,
    // consumed by zivdah-log-server. Keep in sync with the hardcoded <topic> value in
    // zivdah-common's src/main/resources/logback/kafka-appender-include.xml (Logback XML
    // config can't reference this Java constant directly).
    public static final String APP_LOGS = "app-logs";

    // zivdah-chat-service events, published by ChatKafkaProducer; consumed by
    // zivdah-notification-service's NotificationEventConsumer (Phase 5).
    public static final String CHAT_HUMAN_REQUESTED = "chat-human-requested";
    public static final String CHAT_MESSAGE_SENT = "chat-message-sent";
    public static final String CHAT_CONVERSATION_ACCEPTED = "chat-conversation-accepted";
    public static final String CHAT_CONVERSATION_CLOSED = "chat-conversation-closed";

    private KafkaTopics() {}
}
