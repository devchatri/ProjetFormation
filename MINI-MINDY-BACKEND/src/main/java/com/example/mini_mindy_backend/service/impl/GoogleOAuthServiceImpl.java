package com.example.mini_mindy_backend.service.impl;

import com.example.mini_mindy_backend.model.User;
import com.example.mini_mindy_backend.repository.UserRepository;
import com.example.mini_mindy_backend.service.GoogleOAuthService;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthServiceImpl implements GoogleOAuthService {
    private final UserRepository userRepository;
    @Value("${gmail.client.id}")
    private String clientId;
    @Value("${gmail.client.secret}")
    private String clientSecret;
    @Value("${gmail.redirect.uri}")
    private String redirectUri;
    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/gmail.readonly");

    @Override
    public String buildAuthorizationUrl(User user) {
        log.debug("Building authorization URL for user: {}", user.getEmail());
        return new GoogleAuthorizationCodeRequestUrl(clientId, redirectUri, SCOPES).setAccessType("offline").build();
    }

    @Override
    public void exchangeCodeForToken(User user, String code) {
        log.info("Exchanging OAuth2 code for user: {}", user.getEmail());
        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), clientId, clientSecret, code, redirectUri).execute();
            String refreshToken = Optional.ofNullable(tokenResponse.getRefreshToken()).orElseThrow(() -> new IllegalStateException("Google OAuth does not return a refresh token. " + "Make sure that consent is set at project level."));
            user.setGoogleRefreshToken(refreshToken);
            userRepository.save(user);
            log.info("OAuth2 token exchanged successfully for user: {}", user.getEmail());
        } catch (IOException e) {
            log.error("Failed to exchange OAuth2 code for user: {}", user.getEmail(), e);
            throw new RuntimeException("Failed to exchange OAuth2 code", e);
        }
    }

    @Override
    public String exchangeCodeForRefreshToken(String code) {
        log.info("Exchanging OAuth2 code for refresh token");
        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), clientId, clientSecret, code, redirectUri).execute();
            String refreshToken = Optional.ofNullable(tokenResponse.getRefreshToken()).orElseThrow(() -> new IllegalStateException("Google OAuth does not return a refresh token. " + "Make sure that consent is set at project level."));
            log.info("OAuth2 refresh token obtained successfully");
            return refreshToken;
        } catch (IOException e) {
            log.error("Failed to exchange OAuth2 code for refresh token", e);
            throw new RuntimeException("Failed to exchange OAuth2 code", e);
        }
    }
}