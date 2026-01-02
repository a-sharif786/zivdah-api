package com.zivdah.auth.config;

import lombok.Data;

@Data
public class ResponseClass {
    private String status;
    private String message;
    private Object data;

}
