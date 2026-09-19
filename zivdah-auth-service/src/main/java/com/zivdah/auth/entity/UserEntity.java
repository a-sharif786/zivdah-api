package com.zivdah.auth.entity;

import com.zivdah.auth.enums.Role;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    private Long id;
    private String name;
    private String email;
    private String password;
    private String mobile;
    private Role role;
    private boolean active;
    private String mobileOtp;
    private String emailOtp;
    private LocalDateTime otpGeneratedAt;

    // Vendor payout destination — entered by the vendor themselves (see AuthController's
    // update-profile/bank-details endpoints), read by zivdah-payment-service's internal
    // AuthServiceClient when a payout request is approved. Either upiVpa alone, or both
    // bankAccountNumber+bankIfscCode, must be on file for a payout to be requestable.
    private String bankAccountNumber;
    private String bankIfscCode;
    private String upiVpa;
}
