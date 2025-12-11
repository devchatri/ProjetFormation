package com.example.mini_mindy_backend.service;

import com.example.mini_mindy_backend.model.User;

public interface GoogleOAuthService {

    String buildAuthorizationUrl(User user);

    void exchangeCodeForToken(User user, String code);

    // Exchange OAuth code and store refresh token (for registration flow)
    String exchangeCodeForRefreshToken(String code);
}
