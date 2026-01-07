package com.zivdah.auth.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailResponseDTO {
    private String status;       // "success" or "failure"
    private String message;      // e.g., "Reset link sent to email"
}