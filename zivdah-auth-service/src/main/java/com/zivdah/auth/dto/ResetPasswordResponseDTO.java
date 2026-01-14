package com.zivdah.auth.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordResponseDTO {

    private String status;  // "success" or "failure"
    private String message; // "Password reset successfully" or error message
}