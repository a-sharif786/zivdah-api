package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MobileRequestDTO {
    @NotBlank(message = "Mobile number is required")
    @Size(min = 10, max = 15, message = "Mobile number must be at least 10 digits")
    private String mobile;
}