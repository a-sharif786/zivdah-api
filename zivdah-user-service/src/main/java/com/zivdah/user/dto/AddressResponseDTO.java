package com.zivdah.user.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponseDTO {
    private Long id;
    private Long userId;

    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pinCode;

    private Boolean isDefault;
}
