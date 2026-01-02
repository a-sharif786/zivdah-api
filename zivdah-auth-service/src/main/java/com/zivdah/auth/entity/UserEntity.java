package com.zivdah.auth.entity;

import com.zivdah.auth.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String mobile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;


}
