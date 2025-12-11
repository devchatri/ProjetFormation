package com.example.mini_mindy_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;
@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID uuid;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = true)
    private String googleRefreshToken;

}
