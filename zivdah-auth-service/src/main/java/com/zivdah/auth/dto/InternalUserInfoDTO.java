package com.zivdah.auth.dto;

import lombok.*;

// Minimal customer snapshot for other services' internal (no user JWT) reads —
// zivdah-order-service's AuthServiceClient (customer's name/email/mobile for a generated
// invoice) and zivdah-payment-service's AuthServiceClient (a vendor's bank/UPI details on
// file, to build an EcomWorldPay payout request). Deliberately narrower than
// AuthUserResponseDTO (no role/active beyond what's needed) to keep an unauthenticated
// endpoint's exposure minimal, same reasoning as getAdminUserIds()/getActiveDeviceTokens().
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
    private String bankAccountNumber;
    private String bankIfscCode;
    private String upiVpa;
}
