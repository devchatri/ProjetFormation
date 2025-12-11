package com.example.mini_mindy_backend.service;

import com.example.mini_mindy_backend.dto.ChatRequest;
import com.example.mini_mindy_backend.dto.ChatResponse;

public interface ChatService {

    /**
     * Process a user message using RAG:
     * 1. Generate embedding for the user query
     * 2. Search similar emails using pgvector
     * 3. Build context from retrieved emails
     * 4. Generate response using OpenAI GPT
     */
    ChatResponse chat(ChatRequest request, String userId);

}
