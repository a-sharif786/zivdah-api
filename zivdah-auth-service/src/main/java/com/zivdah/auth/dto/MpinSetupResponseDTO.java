package com.zivdah.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// deviceSecret is returned exactly once — the server keeps only its hash. The app must store it
// in Android Keystore / iOS Keychain and send it with every MPIN login.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpinSetupResponseDTO {
    private String deviceId;
    private String deviceSecret;
    private int maxAttempts;
}
