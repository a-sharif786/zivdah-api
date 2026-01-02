package com.zivdah.product.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannerResponseDto {
    private Long id;
    private String imageUrl;
    private String title;
}
