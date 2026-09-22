package com.zivdah.payment.client.dto;

import lombok.*;

// Mirrors auth-service's InternalUserInfoDTO shape — kept local rather than shared via
// zivdah-common since it's a one-off cross-service read, same as
// zivdah-order-service's client.dto.CustomerInfoDto.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorBankDetailsDto {
    private Long id;
    private String name;
    private String email;
    private String mobile;
    private String bankAccountNumber;
    private String bankIfscCode;
    private String upiVpa;
    private String role;

    public boolean hasUpiVpa() {
        return upiVpa != null && !upiVpa.isBlank();
    }

    public boolean hasBankAccount() {
        return bankAccountNumber != null && !bankAccountNumber.isBlank()
                && bankIfscCode != null && !bankIfscCode.isBlank();
    }

    public boolean hasPayoutDestination() {
        return hasUpiVpa() || hasBankAccount();
    }

    public boolean isVendor() {
        return "VENDOR".equalsIgnoreCase(role);
    }
}
