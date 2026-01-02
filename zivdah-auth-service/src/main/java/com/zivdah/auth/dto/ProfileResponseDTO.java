package com.zivdah.auth.dto;

import lombok.Data;

@Data
public class ProfileResponseDTO {
    private String name;
    private String email;
    private String mobile;
    private String address;
}
