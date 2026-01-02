package com.zivdah.auth.controller;

import com.zivdah.auth.config.ResponseClass;
import com.zivdah.auth.dto.ForgotPasswordRequestDTO;
import com.zivdah.auth.dto.ResetPasswordRequestDTO;
import com.zivdah.auth.service.PasswordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/restful/v1/api/auth")
@Slf4j
public class PasswordController {

    @Autowired
    private PasswordService passwordService;

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestBody ForgotPasswordRequestDTO request) {

        ResponseClass response = new ResponseClass();
        Map<String, Object> responseData = new HashMap<>();

        try {
            passwordService.forgotPassword(request);

            responseData.put("statusCode", 200);
            responseData.put("status", "success");
            responseData.put("message", "Reset password link sent successfully");

            response.setData(responseData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {

            responseData.put("statusCode", 400);
            responseData.put("status", "fail");
            responseData.put("message", "Not able to fetch details");
            responseData.put("description", e.getMessage());

            response.setData(responseData);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestBody ResetPasswordRequestDTO request) {

        ResponseClass response = new ResponseClass();
        Map<String, Object> responseData = new HashMap<>();

        try {
            passwordService.resetPassword(request);

            responseData.put("statusCode", 200);
            responseData.put("status", "success");
            responseData.put("message", "Password reset successfully");

            response.setData(responseData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {

            responseData.put("statusCode", 400);
            responseData.put("status", "fail");
            responseData.put("message", "Unable to reset password");
            responseData.put("description", e.getMessage());

            response.setData(responseData);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
