package com.example.mini_mindy_backend.service;

import com.example.mini_mindy_backend.dto.EmailDTO;
import java.util.List;

public interface GmailService {
    List<EmailDTO> getRecentEmailsForUser(String refreshToken, int maxResults) throws Exception;
}
