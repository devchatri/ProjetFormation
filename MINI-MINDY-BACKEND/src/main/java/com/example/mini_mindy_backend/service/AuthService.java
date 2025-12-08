package com.example.mini_mindy_backend.service;

import com.example.mini_mindy_backend.dto.LoginRequest;
import com.example.mini_mindy_backend.dto.LoginResponse;
import com.example.mini_mindy_backend.dto.RegisterRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    LoginResponse register(RegisterRequest request);
}
