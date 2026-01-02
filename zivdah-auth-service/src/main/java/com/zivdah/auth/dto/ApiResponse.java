package com.zivdah.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private String status;       // "success" or "error"
    private String message;      // Human readable message
    private int statusCode;      // HTTP status code
    private T data;              // Any type of data
}