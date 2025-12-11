package com.example.mini_mindy_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryResponse {
    private Long sessionId;
    private List<MessageDto> messages;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageDto {
        private Long id;
        private String role;
        private String content;
        private List<ChatResponse.EmailContext> sources;
        private LocalDateTime createdAt;
    }
}
