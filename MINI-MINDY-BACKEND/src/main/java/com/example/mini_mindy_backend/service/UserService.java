package com.example.mini_mindy_backend.service;

import com.example.mini_mindy_backend.model.User;
import com.example.mini_mindy_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Récupérer la date de dernière synchronisation pour un utilisateur
     */
    public LocalDateTime getLastSyncDate(String userId) {
        try {
            User user = userRepository.findById(UUID.fromString(userId)).orElse(null);
            if (user == null) {
                log.warn("[USER] User not found: {}", userId);
                return null;
            }
            return user.getLastSyncDate();
        } catch (Exception e) {
            log.error("[USER] Error retrieving lastSyncDate for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Mettre à jour la date de dernière synchronisation pour un utilisateur
     */
    public void updateLastSyncDate(String userId, LocalDateTime syncDate) {
        try {
            User user = userRepository.findById(UUID.fromString(userId)).orElse(null);
            if (user == null) {
                log.warn("[USER] User not found: {}", userId);
                return;
            }
            user.setLastSyncDate(syncDate);
            userRepository.save(user);
            log.info("[USER] Updated lastSyncDate for user: {} to {}", userId, syncDate);
        } catch (Exception e) {
            log.error("[USER] Error updating lastSyncDate for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Récupérer l'email de l'utilisateur par son ID
     */
    public String getEmailByUserId(String userId) {
        try {
            User user = userRepository.findById(UUID.fromString(userId)).orElse(null);
            if (user == null) {
                log.warn("[USER] User not found: {}", userId);
                return null;
            }
            return user.getEmail();
        } catch (Exception e) {
            log.error("[USER] Error retrieving email for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Récupérer le Google Refresh Token de l'utilisateur par son ID
     */
    public String getRefreshTokenByUserId(String userId) {
        try {
            User user = userRepository.findById(UUID.fromString(userId)).orElse(null);
            if (user == null) {
                log.warn("[USER] User not found: {}", userId);
                return null;
            }
            return user.getGoogleRefreshToken();
        } catch (Exception e) {
            log.error("[USER] Error retrieving refreshToken for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
}
