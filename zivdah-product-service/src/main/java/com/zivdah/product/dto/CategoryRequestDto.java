package com.zivdah.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequestDto {

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 100)
    private String name;

    // Optional — auto-generated from name (slugified) when not supplied.
    private String slug;

    // Null = top-level category. Set to another category's id to nest it as a subcategory.
    private Long parentId;

    private String imageUrl;

    private Boolean active;
}
