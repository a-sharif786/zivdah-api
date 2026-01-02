package com.zivdah.auth.dto;

import lombok.Data;

@Data
public class ResetPasswordRequestDTO {
    private Long userId;
    private String resetToken;
    private String newPassword;
}
