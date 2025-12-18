package com.example.mini_mindy_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String response;
    private List<EmailContext> sources;
    private Long sessionId; // Return session ID for frontend to track

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailContext {
        private String emailId;
        private String subject;
        private String sender;
        private String date;
        private double similarity;
    }
}
