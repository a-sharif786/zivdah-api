package com.zivdah.order.service;

import com.zivdah.order.client.CouponServiceClient;
import com.zivdah.order.client.ProductServiceClient;
import com.zivdah.order.client.dto.CouponDto;
import com.zivdah.order.client.dto.ProductPriceDto;
import com.zivdah.order.config.OrderPricingProperties;
import com.zivdah.order.dto.OrderItemDto;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Computes an order's price entirely server-side — item prices and vendors from product-service,
 * the coupon discount from coupon-service's coupon definition, tax/delivery/packaging/handling
 * from {@link OrderPricingProperties}. Nothing price-related in the checkout request is trusted:
 * previously the client sent every price and total and the server only checked that they summed
 * up, so a tampered request could buy a whole cart for ₹1 and still get a legitimately PAID order.
 *
 * <p>The formulas intentionally reproduce the storefront's own checkout math (zivdah-web
 * Checkout.jsx / CartContext, coupon-service's applyCoupon), so for an honest client the numbers
 * are identical and nothing about checkout changes.
 */
@Service
@RequiredArgsConstructor
public class OrderPricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ProductServiceClient productServiceClient;
    private final CouponServiceClient couponServiceClient;
    private final OrderPricingProperties pricing;

    @Getter
    @Builder
    public static class PricedLine {
        private final Long productId;
        private final Long vendorId;
        private final Integer quantity;
        private final BigDecimal unitPrice;
        private final BigDecimal subtotal;
    }

    @Getter
    @Builder
    public static class PricedOrder {
        private final List<PricedLine> lines;
        private final BigDecimal subTotal;
        private final BigDecimal taxAmount;
        private final BigDecimal deliveryCharge;
        private final BigDecimal packagingCharge;
        private final BigDecimal handlingCharge;
        private final BigDecimal discountAmount;
        private final String couponCode;
        private final BigDecimal totalAmount;
    }

    public Mono<PricedOrder> price(List<OrderItemDto> items, String couponCode) {
        // concatMap keeps lines in the request's order (flatMap would not).
        return Flux.fromIterable(items)
                .concatMap(this::priceLine)
                .collectList()
                .flatMap(lines -> {
                    BigDecimal subTotal = lines.stream()
                            .map(PricedLine::getSubtotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);
                    boolean hasCoupon = couponCode != null && !couponCode.isBlank();
                    Mono<BigDecimal> discount = hasCoupon
                            ? couponServiceClient.getCoupon(couponCode.trim()).map(c -> couponDiscount(c, subTotal))
                            : Mono.just(BigDecimal.ZERO);
                    return discount.map(d -> buildOrder(lines, subTotal, d, hasCoupon ? couponCode.trim() : null));
                });
    }

    private Mono<PricedLine> priceLine(OrderItemDto item) {
        if (item.getProductId() == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each item must have a productId"));
        }
        return productServiceClient.getProduct(item.getProductId())
                .flatMap(product -> {
                    BigDecimal unitPrice = unitPrice(product);
                    if (unitPrice == null || unitPrice.signum() < 0) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Product " + item.getProductId() + " is not currently for sale"));
                    }
                    return Mono.just(PricedLine.builder()
                            .productId(item.getProductId())
                            .vendorId(product.getVendorId())
                            .quantity(item.getQuantity())
                            .unitPrice(unitPrice)
                            .subtotal(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())))
                            .build());
                });
    }

    // Same rule the storefront uses to show/add a price: discountPrice when set, else price.
    private static BigDecimal unitPrice(ProductPriceDto product) {
        return product.getDiscountPrice() != null ? product.getDiscountPrice() : product.getPrice();
    }

    // Mirrors coupon-service CouponServiceImpl#applyCoupon's validity checks and discount math,
    // minus its usage-limit check: the storefront's own /coupons/apply call already consumed one
    // use (incrementing usedCount), so re-checking the limit here would reject the customer's own
    // last permitted use.
    BigDecimal couponDiscount(CouponDto coupon, BigDecimal subTotal) {
        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isActive()
                || (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom()))
                || (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon " + coupon.getCode() + " is no longer valid");
        }
        if (coupon.getMinOrderAmount() != null && subTotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order is below the coupon's minimum of " + coupon.getMinOrderAmount());
        }
        BigDecimal value = coupon.getDiscountValue() != null ? coupon.getDiscountValue() : BigDecimal.ZERO;
        BigDecimal discount = "PERCENTAGE".equalsIgnoreCase(coupon.getDiscountType())
                ? subTotal.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : value;
        if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discount = coupon.getMaxDiscountAmount();
        }
        // Never more than the goods themselves — a total can't go negative.
        return discount.min(subTotal).setScale(2, RoundingMode.HALF_UP);
    }

    private PricedOrder buildOrder(List<PricedLine> lines, BigDecimal subTotal, BigDecimal discount, String couponCode) {
        BigDecimal tax = subTotal.multiply(pricing.getTaxRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal delivery = subTotal.compareTo(pricing.getFreeDeliveryThreshold()) >= 0
                ? BigDecimal.ZERO : pricing.getDeliveryCharge();
        BigDecimal packaging = pricing.getPackagingCharge();
        BigDecimal handling = pricing.getHandlingCharge();
        BigDecimal total = subTotal.add(tax).add(delivery).add(packaging).add(handling).subtract(discount)
                .setScale(2, RoundingMode.HALF_UP);
        return PricedOrder.builder()
                .lines(lines)
                .subTotal(subTotal)
                .taxAmount(tax)
                .deliveryCharge(delivery.setScale(2, RoundingMode.HALF_UP))
                .packagingCharge(packaging.setScale(2, RoundingMode.HALF_UP))
                .handlingCharge(handling.setScale(2, RoundingMode.HALF_UP))
                .discountAmount(discount)
                .couponCode(couponCode)
                .totalAmount(total)
                .build();
    }
}
