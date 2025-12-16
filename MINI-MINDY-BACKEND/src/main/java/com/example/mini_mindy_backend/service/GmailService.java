package com.example.mini_mindy_backend.service;

import com.example.mini_mindy_backend.dto.EmailDTO;
import java.time.LocalDateTime;
import java.util.List;

public interface GmailService {
    
    /**
     * Get recent emails for a user using their refresh token
     */
    List<EmailDTO> getRecentEmailsForUser(String refreshToken, int maxResults) throws Exception;
    
    /**
     * Check if there are new emails for the user since the last sync date
     */
    boolean hasNewEmails(String userId, LocalDateTime lastSyncDate);
}
