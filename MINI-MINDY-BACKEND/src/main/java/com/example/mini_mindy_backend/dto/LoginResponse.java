package com.example.mini_mindy_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private long expiresAt;
    private String uuid;
    private String email;
}
