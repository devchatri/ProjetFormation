package com.example.mini_mindy_backend.service.impl;

import com.example.mini_mindy_backend.dto.ChatRequest;
import com.example.mini_mindy_backend.dto.ChatResponse;
import com.example.mini_mindy_backend.repository.EmailEmbeddingRepository;
import com.example.mini_mindy_backend.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final EmailEmbeddingRepository emailRepository;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    private static final int TOP_K_RESULTS = 5;

    public ChatServiceImpl(
            EmailEmbeddingRepository emailRepository,
            ChatClient.Builder chatClientBuilder,
            EmbeddingModel embeddingModel
    ) {
        this.emailRepository = emailRepository;
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
    }

    @Override
    public ChatResponse chat(ChatRequest request, String userId) {
        log.info("[RAG] Processing query: {}", request.getMessage());

        try {
            // 1. Generate embedding for user query using Spring AI
            String queryEmbedding = generateEmbedding(request.getMessage());
            log.info("[RAG] Query embedding generated");

            // 2. Search similar emails using pgvector
            List<Object[]> similarEmails = emailRepository.findSimilarByCombinedEmbedding(
                    queryEmbedding, TOP_K_RESULTS
            );
            log.info("[RAG] Found {} similar emails", similarEmails.size());

            // 3. Build context from retrieved emails
            // Query returns: email_id(0), sender(1), receiver(2), sender_domain(3), is_important(4), subject(5), body(6), similarity(7)
            StringBuilder context = new StringBuilder();
            List<ChatResponse.EmailContext> sources = new ArrayList<>();

            for (Object[] row : similarEmails) {
                String emailId = (String) row[0];
                String sender = (String) row[1];
                String subject = row[5] != null ? (String) row[5] : "";
                String body = row[6] != null ? (String) row[6] : "";
                double similarity = row[7] != null ? ((Number) row[7]).doubleValue() : 0.0;

                // Add to context for GPT
                context.append("\n---\n");
                context.append("Email ID: ").append(emailId).append("\n");
                context.append("Email from: ").append(sender).append("\n");
                context.append("Subject: ").append(subject).append("\n");
                context.append("Content: ").append(truncateText(body, 500)).append("\n");

                // Add to sources list
                sources.add(ChatResponse.EmailContext.builder()
                        .emailId(emailId)
                        .subject(subject)
                        .sender(sender)
                        .similarity(similarity)
                        .build());
            }
            
            log.info("[RAG] {} emails added to context", sources.size());

            // 4. Generate response using Spring AI ChatClient
            String systemPrompt = buildSystemPrompt(context.toString());
            String response = generateChatResponse(systemPrompt, request.getMessage());

            return ChatResponse.builder()
                    .response(response)
                    .sources(sources)
                    .build();

        } catch (Exception e) {
            log.error("[RAG] Error processing chat: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .response("Sorry, I encountered an error while processing your request: " + e.getMessage())
                    .sources(Collections.emptyList())
                    .build();
        }
    }

    private String generateEmbedding(String text) {
        // Use Spring AI EmbeddingModel
        float[] embedding = embeddingModel.embed(text);
        
        // Convert to pgvector format [x,y,z,...]
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String buildSystemPrompt(String emailContext) {
        return """
            You are Mini-Mindy, a helpful AI email assistant. Your role is to help users understand and manage their emails.
            
            You have access to the user's emails. Use the following email context to answer their questions:
            
            === EMAIL CONTEXT ===
            %s
            === END CONTEXT ===
            
            Instructions:
            - Answer questions based ONLY on the provided email context
            - If the email context is empty or no emails are relevant, say "I couldn't find that information in your emails"
            - Be concise but helpful
            - When mentioning an email, reference its sender and subject
            - Only mention emails that are DIRECTLY relevant to the user's question
            - If asked about a specific sender (like Temu, Netflix, etc.), only reference emails FROM that sender
            - Respond in the same language as the user's question
            """.formatted(emailContext);
    }

    private String generateChatResponse(String systemPrompt, String userMessage) {
        // Use Spring AI ChatClient with fluent API
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
