package com.zivdah.auth.entity;

import com.zivdah.auth.enums.Role;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    private Long id;
    private String name;
    private String email;
    private String password;
    private String mobile;
    private Role role;
    @Column("is_active")
    private boolean active;
    private String mobileOtp;
    private String emailOtp;
    private LocalDateTime otpGeneratedAt;
}
