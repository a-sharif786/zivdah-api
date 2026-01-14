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
}