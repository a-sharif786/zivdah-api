package com.zivdah.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserProfileDTO {

    @NotBlank(message = "Name is required")
    private String name;

//    @Email(message = "Invalid email format")
//    @NotBlank(message = "Email is required")
//    private String email;

    // Vendor payout destination fields — optional and independent of `name`. Left out of a
    // request (null), the existing stored value is kept as-is rather than wiped; see
    // AuthServiceImpl#updateProfile.
    private String bankAccountNumber;
    private String bankIfscCode;
    private String upiVpa;
}