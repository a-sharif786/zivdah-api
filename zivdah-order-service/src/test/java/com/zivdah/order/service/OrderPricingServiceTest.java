package com.zivdah.order.service;

import com.zivdah.order.client.CouponServiceClient;
import com.zivdah.order.client.ProductServiceClient;
import com.zivdah.order.client.dto.CouponDto;
import com.zivdah.order.client.dto.ProductPriceDto;
import com.zivdah.order.config.OrderPricingProperties;
import com.zivdah.order.dto.OrderItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPricingServiceTest {

    @Mock private ProductServiceClient productServiceClient;
    @Mock private CouponServiceClient couponServiceClient;

    private OrderPricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new OrderPricingService(productServiceClient, couponServiceClient, new OrderPricingProperties());
    }

    private static OrderItemDto item(long productId, int qty, String clientPrice) {
        return OrderItemDto.builder().productId(productId).quantity(qty)
                .price(new BigDecimal(clientPrice)).vendorId(999L).build();
    }

    private void product(long id, String price, String discountPrice, long vendorId) {
        when(productServiceClient.getProduct(id)).thenReturn(Mono.just(ProductPriceDto.builder()
                .id(id).price(new BigDecimal(price))
                .discountPrice(discountPrice == null ? null : new BigDecimal(discountPrice))
                .vendorId(vendorId).build()));
    }

    private static CouponDto coupon(String type, String value, String min, String max) {
        return CouponDto.builder().code("SAVE").discountType(type).discountValue(new BigDecimal(value))
                .minOrderAmount(min == null ? null : new BigDecimal(min))
                .maxDiscountAmount(max == null ? null : new BigDecimal(max))
                .active(true).validFrom(LocalDateTime.now().minusDays(1)).validUntil(LocalDateTime.now().plusDays(1))
                .build();
    }

    @Test
    void pricesFromProductServiceNotFromRequest_matchingStorefrontMath() {
        // storefront: price = discountPrice ?? price; delivery 40 below 500; tax 5% of subtotal
        product(1L, "120.00", "100.00", 7L);   // discounted -> 100
        product(2L, "50.00", null, 8L);        // no discount -> 50
        List<OrderItemDto> items = List.of(item(1L, 2, "1.00"), item(2L, 1, "1.00")); // client lies: ₹1

        StepVerifier.create(pricingService.price(items, null))
                .assertNext(p -> {
                    assertThat(p.getSubTotal()).isEqualByComparingTo("250.00");
                    assertThat(p.getTaxAmount()).isEqualByComparingTo("12.50");
                    assertThat(p.getDeliveryCharge()).isEqualByComparingTo("40.00");
                    assertThat(p.getDiscountAmount()).isEqualByComparingTo("0");
                    assertThat(p.getTotalAmount()).isEqualByComparingTo("302.50");
                    assertThat(p.getLines()).extracting(OrderPricingService.PricedLine::getUnitPrice)
                            .usingElementComparator(BigDecimal::compareTo)
                            .containsExactly(new BigDecimal("100"), new BigDecimal("50"));
                    // vendor comes from the product, not the request's vendorId=999
                    assertThat(p.getLines()).extracting(OrderPricingService.PricedLine::getVendorId).containsExactly(7L, 8L);
                })
                .verifyComplete();
        verifyNoInteractions(couponServiceClient);
    }

    @Test
    void freeDeliveryAtThreshold() {
        product(1L, "500.00", null, 7L);
        StepVerifier.create(pricingService.price(List.of(item(1L, 1, "500")), null))
                .assertNext(p -> {
                    assertThat(p.getDeliveryCharge()).isEqualByComparingTo("0");
                    assertThat(p.getTotalAmount()).isEqualByComparingTo("525.00");
                })
                .verifyComplete();
    }

    @Test
    void percentageCouponRecomputedAndCapped() {
        product(1L, "1000.00", null, 7L);
        when(couponServiceClient.getCoupon("SAVE")).thenReturn(Mono.just(coupon("PERCENTAGE", "10", null, "80")));
        StepVerifier.create(pricingService.price(List.of(item(1L, 1, "1000")), "SAVE"))
                .assertNext(p -> {
                    assertThat(p.getDiscountAmount()).isEqualByComparingTo("80.00"); // 100 capped at 80
                    assertThat(p.getTotalAmount()).isEqualByComparingTo("970.00");   // 1000 + 50 tax + 0 - 80
                    assertThat(p.getCouponCode()).isEqualTo("SAVE");
                })
                .verifyComplete();
    }

    @Test
    void fixedCouponNeverExceedsSubtotal() {
        product(1L, "30.00", null, 7L);
        when(couponServiceClient.getCoupon("SAVE")).thenReturn(Mono.just(coupon("FIXED", "100", null, null)));
        StepVerifier.create(pricingService.price(List.of(item(1L, 1, "30")), "SAVE"))
                .assertNext(p -> assertThat(p.getDiscountAmount()).isEqualByComparingTo("30.00"))
                .verifyComplete();
    }

    @Test
    void couponBelowMinimumIsRejected() {
        product(1L, "100.00", null, 7L);
        when(couponServiceClient.getCoupon("SAVE")).thenReturn(Mono.just(coupon("FIXED", "20", "200", null)));
        StepVerifier.create(pricingService.price(List.of(item(1L, 1, "100")), "SAVE"))
                .expectErrorSatisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT))
                .verify();
    }

    @Test
    void expiredCouponIsRejected() {
        product(1L, "100.00", null, 7L);
        CouponDto expired = coupon("FIXED", "20", null, null);
        expired.setValidUntil(LocalDateTime.now().minusMinutes(1));
        when(couponServiceClient.getCoupon("SAVE")).thenReturn(Mono.just(expired));
        StepVerifier.create(pricingService.price(List.of(item(1L, 1, "100")), "SAVE"))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void unavailableProductFailsTheWholeOrder() {
        when(productServiceClient.getProduct(1L)).thenReturn(Mono.error(
                new ResponseStatusException(HttpStatus.CONFLICT, "Product 1 is no longer available")));
        StepVerifier.create(pricingService.price(List.of(item(1L, 1, "10")), null))
                .expectError(ResponseStatusException.class)
                .verify();
    }
}
