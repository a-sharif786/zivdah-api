package com.zivdah.auth.dto;

import com.zivdah.auth.enums.Role;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserResponseDTO {
    private Long userId;
    private String name;
    private String email;
    private String mobile;
    private Role role;
}