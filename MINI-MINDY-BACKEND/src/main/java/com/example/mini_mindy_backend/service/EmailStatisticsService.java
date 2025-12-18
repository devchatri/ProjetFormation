package com.example.mini_mindy_backend.service;

import com.example.mini_mindy_backend.dto.EmailStatisticsDTO;

public interface EmailStatisticsService {
    
    /**
     * Get comprehensive email statistics for the authenticated user
     * This queries Gmail API directly
     */
    EmailStatisticsDTO getEmailStatistics(String userEmail) throws Exception;
}
