package com.example.mini_mindy_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleOAuthRequest {
    @NotBlank(message = "Authorization code is required")
    private String code;
}
