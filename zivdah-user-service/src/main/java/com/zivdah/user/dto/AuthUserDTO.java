package com.zivdah.user.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserDTO {
    private Long userId;
    private String name;
    private String email;
    private String mobile;
    private String role;
}