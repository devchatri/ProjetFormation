package com.example.mini_mindy_backend.service.impl;


import com.example.mini_mindy_backend.dto.LoginRequest;
import com.example.mini_mindy_backend.dto.LoginResponse;
import com.example.mini_mindy_backend.dto.RegisterRequest;
import com.example.mini_mindy_backend.model.User;
import com.example.mini_mindy_backend.repository.UserRepository;
import com.example.mini_mindy_backend.service.AuthService;
import com.example.mini_mindy_backend.util.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public LoginResponse login(LoginRequest request) {


        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid password");
        }

        String token = jwtService.generateToken(user.getEmail());

        return new LoginResponse(
                token,
                user.getEmail()
        );
    }
    @Override
    public LoginResponse register(RegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());

        return new LoginResponse(token, user.getEmail());
    }

}

