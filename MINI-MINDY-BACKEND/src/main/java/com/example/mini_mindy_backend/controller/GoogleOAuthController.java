package com.example.mini_mindy_backend.controller;

import com.example.mini_mindy_backend.dto.GoogleCompleteRegistrationRequest;
import com.example.mini_mindy_backend.dto.GoogleOAuthRequest;
import com.example.mini_mindy_backend.dto.LoginResponse;
import com.example.mini_mindy_backend.dto.RegisterRequest;
import com.example.mini_mindy_backend.model.User;
import com.example.mini_mindy_backend.repository.UserRepository;
import com.example.mini_mindy_backend.service.AuthService;
import com.example.mini_mindy_backend.service.GoogleOAuthService;
import com.example.mini_mindy_backend.util.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthController {

    private final AuthService authService;
    private final GoogleOAuthService googleOAuthService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @PostMapping("/complete-registration")
    public ResponseEntity<?> completeRegistrationWithGoogle(
            @Valid @RequestBody GoogleCompleteRegistrationRequest request) {
        try {
            log.info("Completing registration with Google for email: {}", request.getEmail());

            // Exchange Google authorization code for refresh token
            String refreshToken = googleOAuthService.exchangeCodeForRefreshToken(request.getCode());

            if (refreshToken == null || refreshToken.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("code", "INVALID_REFRESH_TOKEN", "message", "Failed to get refresh token from Google"));
            }

            // Register user with refresh token
            LoginResponse response = authService.registerWithGoogleRefreshToken(
                    new RegisterRequest(request.getEmail(), request.getPassword()),
                    refreshToken
            );

            log.info("User registration with Google completed: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "EMAIL_ALREADY_EXISTS", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error completing Google registration: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "REGISTRATION_FAILED", "message", e.getMessage()));
        }
    }

    @PostMapping("/exchange-code")
    public ResponseEntity<?> exchangeGoogleCode(
            @Valid @RequestBody GoogleOAuthRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            // Extract JWT token from Authorization header
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("code", "MISSING_TOKEN", "message", "Authorization header required"));
            }

            String token = authHeader.substring(7);
            String email = jwtService.extractEmail(token);

            // Find user by email
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

            // Exchange code and store refresh token for this user
            googleOAuthService.exchangeCodeForToken(user, request.getCode());

            return ResponseEntity.ok(Map.of("message", "Google account authorized successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "OAUTH_EXCHANGE_FAILED", "message", e.getMessage()));
        }
    }
}
