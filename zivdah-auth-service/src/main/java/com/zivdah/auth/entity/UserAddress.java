package com.zivdah.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "user_address")
@Data
public class UserAddress {

    @Id
    @Column(name = "user_id")
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private UserEntity user;

    private String addressLine1;
    @Column(name = "house_number")
    private String houseNumber;
    private String city;
    private String state;
    private String pinCode;
}
