package com.zivdah.auth.dto;

import lombok.Data;

@Data
public class UpdateUserAddressRequestDTO {
    private Long userId;
    private String name;
    private String password;
    private String addressLine1;
    private String houseNumber;
    private String city;
    private String state;
    private String pinCode;
}
