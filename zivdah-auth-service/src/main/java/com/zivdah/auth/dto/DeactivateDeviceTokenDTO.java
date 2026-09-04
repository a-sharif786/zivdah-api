package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Body for the internal PATCH /restful/v1/api/auth/internal/device-tokens/deactivate —
// called by zivdah-notification-service when Firebase reports a token as
// unregistered/invalid, so future sends stop targeting it.
@Data
public class DeactivateDeviceTokenDTO {
    @NotBlank(message = "Device token is required")
    private String fcmToken;
}
