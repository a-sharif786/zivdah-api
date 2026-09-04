package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Body for POST /restful/v1/api/auth/device-tokens — userId/role come from the caller's JWT,
// not this DTO. deviceType is optional and defaults to WEB server-side (see
// AuthServiceImpl#registerDeviceToken) so existing web callers that don't send it keep working.
@Data
public class DeviceTokenRequestDTO {
    @NotBlank(message = "Device token is required")
    private String fcmToken;

    private String deviceType; // ANDROID, IOS, WEB
}
