package com.zivdah.chat.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Mirrors product-service's own ProductResponseDto shape verbatim for the fields the plan calls
// out (id, name, imageUrl, price, discountPrice, inStock, unit, category, vendorId) — the
// PRODUCT_SEARCH intent returns these directly as the `products` field on
// BotMessageResponseDto so the frontend can render a product card with zero adapter code.
// `organic` is additionally carried (present on product-service's real DTO too) purely to
// support the in-memory "organic" qualifier filter — no server-side filter exists for it.
@Getter
@Setter
public class ProductSummaryDto {
    private Long id;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private Boolean inStock;
    private String unit;
    // Raw ProductCategory enum name as a String (e.g. "VEGETABLE") — see OrderSummaryDto's
    // note on why these narrow DTOs use String instead of duplicating another service's enum.
    private String category;
    private Long vendorId;
    private Boolean organic;
}
