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
        String userId = authentication != null ? authentication.getName() : "anonymous";
        log.info("[CHAT] User {} asking: {}", userId, request.getMessage());

        ChatResponse response = chatService.chat(request, userId);

        log.info("[CHAT] Response generated with {} sources", response.getSources().size());
        return ResponseEntity.ok(response);
    }
}
