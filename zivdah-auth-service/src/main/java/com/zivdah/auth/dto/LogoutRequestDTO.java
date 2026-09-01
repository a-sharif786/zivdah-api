package com.zivdah.auth.dto;

import lombok.Data;

// Optional body for POST /restful/v1/api/auth/logout. When fcmToken is present, only that
// one device's token is deactivated — logging out of one browser/device shouldn't sign a
// user's other devices out of push. Omit it (or leave the body empty) to keep the old
// behavior of just clearing the JWT session.
@Data
public class LogoutRequestDTO {
    private String fcmToken;
}
