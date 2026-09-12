package com.zivdah.order.client.dto;

import lombok.*;

// Mirrors auth-service's InternalUserInfoDTO shape (id, name, email, mobile) — kept local
// rather than shared via zivdah-common since it's a one-off cross-service read, same as how
// PaymentServiceClient/OrderServiceClient elsewhere don't share DTOs across service boundaries.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerInfoDto {
    private Long id;
    private String name;
    private String email;
    private String mobile;
}
