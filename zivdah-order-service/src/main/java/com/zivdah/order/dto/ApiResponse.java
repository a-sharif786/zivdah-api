package com.zivdah.order.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private String status;
    private int statusCode;
    private String message;
    private T data;
}