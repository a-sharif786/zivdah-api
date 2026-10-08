package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppVersionStatusRequestDTO {
    @NotNull(message = "active is required")
    private Boolean active;
}
