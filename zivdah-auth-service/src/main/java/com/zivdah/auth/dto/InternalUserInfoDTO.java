package com.zivdah.auth.dto;

import lombok.*;

// Minimal customer snapshot for other services' internal (no user JWT) reads —
// zivdah-order-service's AuthServiceClient (customer's name/email/mobile for a generated
// invoice) and zivdah-payment-service's AuthServiceClient (a vendor's bank/UPI details on
// file, to build an EcomWorldPay payout request). Deliberately narrower than
// AuthUserResponseDTO (no active-status beyond what's needed) to keep an unauthenticated
// endpoint's exposure minimal, same reasoning as getAdminUserIds()/getActiveDeviceTokens().
// role IS included (unlike active-status) so payment-service can verify an admin-initiated
// payout's target is actually a VENDOR before treating it as one.
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
    private String role;
}
