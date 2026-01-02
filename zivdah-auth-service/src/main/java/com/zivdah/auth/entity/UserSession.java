package com.zivdah.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_sessions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String token;

    @Column(name = "device_token")
    private String deviceToken;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}