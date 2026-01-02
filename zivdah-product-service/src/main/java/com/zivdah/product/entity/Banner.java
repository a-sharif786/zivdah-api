package com.zivdah.product.entity;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "banners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String imageUrl;   // e.g. /images/banner1.jpg

    private String title;

    private Boolean active;
}
