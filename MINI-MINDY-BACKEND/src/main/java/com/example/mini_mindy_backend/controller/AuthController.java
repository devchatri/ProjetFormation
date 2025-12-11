package com.example.mini_mindy_backend.controller;

import com.example.mini_mindy_backend.dto.*;
import com.example.mini_mindy_backend.service.AuthService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;


@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;


    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @NotNull @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }


}
