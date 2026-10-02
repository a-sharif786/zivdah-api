package com.zivdah.order.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// Checkout charges the server recomputes every order total from (see OrderPricingService).
// Bound from order.pricing.* in application.yaml — the defaults below mirror the storefront's
// own checkout math (zivdah-web Checkout.jsx) and must be kept in step with it.
@Component
@ConfigurationProperties(prefix = "order.pricing")
@Getter
@Setter
public class OrderPricingProperties {
    private BigDecimal taxRate = new BigDecimal("0.05");
    private BigDecimal freeDeliveryThreshold = new BigDecimal("500");
    private BigDecimal deliveryCharge = new BigDecimal("40");
    private BigDecimal packagingCharge = BigDecimal.ZERO;
    private BigDecimal handlingCharge = BigDecimal.ZERO;
}
