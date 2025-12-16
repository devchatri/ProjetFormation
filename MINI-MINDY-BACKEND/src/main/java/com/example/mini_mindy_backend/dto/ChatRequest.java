package com.example.mini_mindy_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatRequest {
    @JsonProperty("userId")
    private String userId; 
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("chatHistory")
    private List<ChatMessage> chatHistory; 
    
    @Data
    @NoArgsConstructor
    public static class ChatMessage {
        @JsonProperty("role")
        private String role;
        
        @JsonProperty("content")
        private String content;
    }
}

