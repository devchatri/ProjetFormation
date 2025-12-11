package com.example.mini_mindy_backend.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private Long sessionId; // Optional: if null, creates new session or uses existing
}
