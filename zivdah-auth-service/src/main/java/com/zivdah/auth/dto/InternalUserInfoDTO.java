package com.zivdah.auth.dto;

import lombok.*;

// Minimal customer snapshot for other services' internal (no user JWT) reads — currently
// zivdah-order-service's AuthServiceClient, which needs a customer's name/email/mobile to
// print on a generated invoice. Deliberately narrower than AuthUserResponseDTO (no role/active/id
// beyond what's needed) to keep an unauthenticated endpoint's exposure minimal, same reasoning
// as getAdminUserIds()/getActiveDeviceTokens().
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalUserInfoDTO {
    private Long id;
    private String name;
    private String email;
    private String mobile;
}
