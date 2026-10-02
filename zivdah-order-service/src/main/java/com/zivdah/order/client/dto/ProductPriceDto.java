package com.zivdah.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

// The subset of product-service's ProductResponseDto that order pricing needs.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductPriceDto {
    private Long id;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private Long vendorId;
}
