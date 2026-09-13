package com.zivdah.chat.client.dto;

import lombok.Getter;
import lombok.Setter;

// Narrow mirror of every other service's own ApiResponse<T> envelope shape ({status, message,
// statusCode, data}) — copied per-service by convention in this codebase (no shared response
// DTO module), same as zivdah-delivery-service's own ApiResponse copy that OrderServiceClient
// there unwraps. Only used to deserialize a downstream service's response long enough to pull
// out `data`; never returned from this service's own controllers.
@Getter
@Setter
public class ApiEnvelope<T> {
    private String status;
    private String message;
    private int statusCode;
    private T data;
}
