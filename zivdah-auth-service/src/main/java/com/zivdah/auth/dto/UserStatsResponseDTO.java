package com.zivdah.auth.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatsResponseDTO {
    private long totalUsers;
    private long totalAdmins;
    private long totalVendors;
    private long totalCustomers;
}
