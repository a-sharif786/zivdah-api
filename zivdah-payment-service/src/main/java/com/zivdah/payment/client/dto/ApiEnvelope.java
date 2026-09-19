package com.zivdah.payment.client.dto;

import lombok.*;

// Mirrors the {status, statusCode, message, data} envelope every service's own ApiResponse<T>
// wraps responses in — needed here purely to unwrap a GET response from auth-service's
// WebClient call, not to build one ourselves. Same pattern as
// zivdah-order-service's client.dto.ApiEnvelope.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiEnvelope<T> {
    private String status;
    private int statusCode;
    private String message;
    private T data;
}
