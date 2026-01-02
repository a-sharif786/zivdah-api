package com.zivdah.product.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true) // Helps if class versions change
public class ApiResponse<T> implements Serializable {
    private String status;
    private String message;
    private int statusCode;
    private T data;
}