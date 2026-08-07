package com.zivdah.product.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannerRequestDto {

    private String title;

    private Boolean active;
}
