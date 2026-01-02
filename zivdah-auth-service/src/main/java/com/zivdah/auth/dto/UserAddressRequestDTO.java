package com.zivdah.auth.dto;

import lombok.Data;

@Data
public class UserAddressRequestDTO {

    private Long userId;
    private String addressLine1;
    private String houseNumber;
    private String city;
    private String state;
    private String pinCode;
}
