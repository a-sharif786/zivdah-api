package com.zivdah.auth.dto;

import com.zivdah.auth.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponseDTO {

    private Long id;
    private String mobile;
    private String name;

    private String email;
    private Role role;
    private String token;
}