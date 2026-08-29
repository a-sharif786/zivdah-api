package com.zivdah.common.constants;

public class KafkaTopics {
    public static final String ORDER_CREATED = "order-created";
    public static final String PAYMENT_COMPLETED = "payment-completed";
    public static final String PRODUCT_CREATED = "product-created";

    // Log events shipped by com.zivdah.common.logging.KafkaLogAppender from every service,
    // consumed by zivdah-log-server. Keep in sync with the hardcoded <topic> value in
    // zivdah-common's src/main/resources/logback/kafka-appender-include.xml (Logback XML
    // config can't reference this Java constant directly).
    public static final String APP_LOGS = "app-logs";

    private KafkaTopics() {}
}
