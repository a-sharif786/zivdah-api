package com.zivdah.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WishlistRequestDto {

    @NotNull
    private Boolean fav;
}