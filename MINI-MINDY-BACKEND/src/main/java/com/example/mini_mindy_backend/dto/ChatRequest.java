package com.example.mini_mindy_backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private String message;
    private List<ChatMessage> chatHistory; // Conversation history for context
    
    @Data
    public static class ChatMessage {
        private String role; // "user" or "assistant"
        private String content;
    }
}

