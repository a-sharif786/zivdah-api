package com.zivdah.auth.controller;
import com.zivdah.auth.dto.ProfileRequestDTO;
import com.zivdah.auth.dto.ProfileResponseDTO;
import com.zivdah.auth.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/restful/v1/api/auth")
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    @PostMapping("/userProfile")
    public ResponseEntity<?> getProfile(@RequestBody ProfileRequestDTO request) {

        Map<String, Object> response = new HashMap<>();

        try {
            ProfileResponseDTO profile = profileService.getProfile(request.getUserId());

            response.put("statusCode", 200);
            response.put("status", "success");
            response.put("message", "Profile fetched successfully");
            response.put("data", profile);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("statusCode", 400);
            response.put("status", "fail");
            response.put("message", "Unable to fetch profile");
            response.put("description", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}
