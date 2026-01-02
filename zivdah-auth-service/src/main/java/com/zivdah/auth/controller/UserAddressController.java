package com.zivdah.auth.controller;

import com.zivdah.auth.dto.UpdateUserAddressRequestDTO;
import com.zivdah.auth.dto.UserAddressRequestDTO;
import com.zivdah.auth.service.UserAddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/restful/v1/api/auth")
public class UserAddressController {

    @Autowired
    private UserAddressService userAddressService;

    @PostMapping("/saveUserAddress")
    public ResponseEntity<?> saveAddress(@RequestBody UserAddressRequestDTO request) {

        Map<String, Object> response = new HashMap<>();

        try {
            userAddressService.saveAddress(request);

            response.put("statusCode", 200);
            response.put("status", "success");
            response.put("message", "Address saved successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            response.put("statusCode", 400);
            response.put("status", "fail");
            response.put("message", "Unable to save address");
            response.put("description", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/updateUserAddress")
    public ResponseEntity<?> updateUserAddress(
            @RequestBody UpdateUserAddressRequestDTO request) {

        Map<String, Object> response = new HashMap<>();

        try {
            userAddressService.updateUserAndAddress(request);

            response.put("statusCode", 200);
            response.put("status", "success");
            response.put("message", "User and address updated successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            response.put("statusCode", 400);
            response.put("status", "fail");
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}
