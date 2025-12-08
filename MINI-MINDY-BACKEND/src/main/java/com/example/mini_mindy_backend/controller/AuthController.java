package com.example.mini_mindy_backend.controller;

import com.example.mini_mindy_backend.dto.LoginRequest;
import com.example.mini_mindy_backend.dto.LoginResponse;
import com.example.mini_mindy_backend.dto.RegisterRequest;
import com.example.mini_mindy_backend.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @NotNull @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @NotNull @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
