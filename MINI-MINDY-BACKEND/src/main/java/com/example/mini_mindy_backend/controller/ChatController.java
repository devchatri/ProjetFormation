package com.example.mini_mindy_backend.controller;

import com.example.mini_mindy_backend.dto.ChatRequest;
import com.example.mini_mindy_backend.dto.ChatResponse;
import com.example.mini_mindy_backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            Authentication authentication
    ) {
        // Log what we receive
        log.info("[CHAT] Received request - userId: '{}', message: '{}', chatHistory size: {}", 
            request.getUserId(), 
            request.getMessage(),
            request.getChatHistory() != null ? request.getChatHistory().size() : 0);
        
        // Use userId from request if provided, otherwise fallback to authentication
        String userId = request.getUserId() != null && !request.getUserId().isEmpty() 
            ? request.getUserId()
            : (authentication != null ? authentication.getName() : "anonymous");
        
        log.info("[CHAT] Using userId: {} (from request: {})", userId, request.getUserId() != null);
        log.info("[CHAT] User {} asking: {}", userId, request.getMessage());

        ChatResponse response = chatService.chat(request, userId);

        log.info("[CHAT] Response generated with {} sources", response.getSources().size());
        return ResponseEntity.ok(response);
    }
}
