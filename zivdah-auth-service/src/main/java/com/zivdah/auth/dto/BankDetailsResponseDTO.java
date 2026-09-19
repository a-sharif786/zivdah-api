package com.zivdah.auth.dto;

import lombok.*;

// Read-side counterpart to UpdateUserProfileDTO's bank fields — lets the vendor UI prefill
// its bank-details form before the vendor makes any edit, without pulling back the wider
// UserResponseDTO/LoginResponseDTO shape.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankDetailsResponseDTO {
    private String bankAccountNumber;
    private String bankIfscCode;
    private String upiVpa;
}
