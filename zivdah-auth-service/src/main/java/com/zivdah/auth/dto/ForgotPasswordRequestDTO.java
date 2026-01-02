package com.zivdah.auth.dto;


import lombok.Data;

@Data
public class ForgotPasswordRequestDTO {

    private String body;

    private String to;

    private String subject;
}
