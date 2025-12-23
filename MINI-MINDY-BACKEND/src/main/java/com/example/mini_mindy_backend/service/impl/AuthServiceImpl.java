package com.example.mini_mindy_backend.service.impl;

import com.example.mini_mindy_backend.dto.LoginRequest;
import com.example.mini_mindy_backend.dto.LoginResponse;
import com.example.mini_mindy_backend.dto.RegisterRequest;
import com.example.mini_mindy_backend.model.User;
import com.example.mini_mindy_backend.repository.UserRepository;
import com.example.mini_mindy_backend.service.AuthService;
import com.example.mini_mindy_backend.util.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
import com.example.mini_mindy_backend.service.AirflowService;

public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AirflowService airflowService;

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());
        
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found: {}", request.getEmail());
                    return new UsernameNotFoundException("User not found");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed - invalid password for user: {}", request.getEmail());
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getEmail());
        long expiresAt = jwtService.extractExpiration(token).getTime();

        log.info("Login successful for user: {}", request.getEmail());

        // Si l'utilisateur a un refresh token Google, lancer Airflow
        if (user.getGoogleRefreshToken() != null && !user.getGoogleRefreshToken().isEmpty()) {
            airflowService.triggerPipelineAsync(user.getEmail(), user.getGoogleRefreshToken());
        }

        return new LoginResponse(
                token,
                expiresAt,
                user.getUuid().toString(),
                user.getEmail()
        );
    }

    @Override
    public LoginResponse register(RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());
        
        // Validate that email doesn't exist
        userRepository.findByEmail(request.getEmail()).ifPresent(existingUser -> {
            log.warn("Registration failed - email already in use: {}", request.getEmail());
            throw new IllegalArgumentException("This email is already in use");
        });

        // Create and save new user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        
        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getEmail());

        // Generate JWT token
        String token = jwtService.generateToken(savedUser.getEmail());
        long expiresAt = jwtService.extractExpiration(token).getTime();

        return new LoginResponse(
                token,
                expiresAt,
                savedUser.getUuid().toString(),
                savedUser.getEmail()
        );
    }

    @Override
    public LoginResponse registerWithGoogleRefreshToken(RegisterRequest request, String refreshToken) {
        log.info("Registration with Google for email: {}", request.getEmail());
        
        // Validate that email doesn't exist
        userRepository.findByEmail(request.getEmail()).ifPresent(existingUser -> {
            log.warn("Registration failed - email already in use: {}", request.getEmail());
            throw new IllegalArgumentException("This email is already in use");
        });

        // Create and save new user with Google refresh token
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setGoogleRefreshToken(refreshToken);
        
        User savedUser = userRepository.save(user);
        log.info("User registered with Google: {}", savedUser.getEmail());

        // Generate JWT token
        String token = jwtService.generateToken(savedUser.getEmail());
        long expiresAt = jwtService.extractExpiration(token).getTime();



        return new LoginResponse(
            token,
            expiresAt,
            savedUser.getUuid().toString(),
            savedUser.getEmail()
        );
    }

}

