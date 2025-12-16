package com.example.mini_mindy_backend.service.impl;

import com.example.mini_mindy_backend.dto.ChatRequest;
import com.example.mini_mindy_backend.dto.ChatResponse;
import com.example.mini_mindy_backend.repository.EmailEmbeddingRepository;
import com.example.mini_mindy_backend.service.ChatService;
import com.example.mini_mindy_backend.service.GmailService;
import com.example.mini_mindy_backend.service.AirflowService;
import com.example.mini_mindy_backend.service.UserService;
import com.example.mini_mindy_backend.util.EmailContextResult;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jakarta.annotation.PostConstruct;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int TOP_K_RESULTS = 5;
    private static final int MAX_BODY_LENGTH = 500;

    private final EmailEmbeddingRepository emailRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingModel embeddingModel;
    private final GmailService gmailService;
    private final AirflowService airflowService;
    private final UserService userService;

    private ChatClient chatClient;

    @PostConstruct
    public void init() {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public ChatResponse chat(ChatRequest request, String userId) {
        log.info("[RAG] Processing query for user: {} - Query: {}", userId, request.getMessage());

        try {
            // 0. Check for new emails and trigger Airflow if necessary
            verifyAndTriggerEmailSync(userId);

            // 1. Build conversation history
            String conversationHistory = buildConversationHistory(request);

            // 2. Generate embedding for user query
            String queryEmbedding = generateEmbedding(request.getMessage());
            log.info("[RAG] Query embedding generated");

            // 3. Search for similar emails
            List<Object[]> similarEmails = findSimilarEmailsByUser(queryEmbedding, userId);
            if (similarEmails.isEmpty()) {
                log.warn("[RAG] No similar emails found for user: {}", userId);
                return buildNoResultsResponse();
            }

            // 4. Build email context
            EmailContextResult contextResult = buildEmailContext(similarEmails);

            // 5. Generate user response
            String systemPrompt = buildSystemPrompt(contextResult.getContext());
            String response = generateChatResponse(systemPrompt, conversationHistory);

            return ChatResponse.builder()
                    .response(response)
                    .sources(contextResult.getSources())
                    .build();

        } catch (Exception e) {
            log.error("[RAG] Error processing chat for user {}: {}", userId, e.getMessage(), e);
            return buildErrorResponse(e);
        }
    }

    private void verifyAndTriggerEmailSync(String userId) {
        try {
            log.info("[SYNC] Checking for new emails for user: {}", userId);
            
            // Retrieve user email and refresh token
            String email = userService.getEmailByUserId(userId);
            String refreshToken = userService.getRefreshTokenByUserId(userId);
            
            if (email == null || refreshToken == null) {
                log.warn("[SYNC] Email or RefreshToken not found for user: {}", userId);
                return;
            }
            
            // Get last sync date
            LocalDateTime lastSyncDate = userService.getLastSyncDate(userId);
            
            // Check if this is first time usage
            boolean isFirstTime = lastSyncDate == null;
            
            // Check for new emails via Gmail API
            if (isFirstTime || gmailService.hasNewEmails(userId, lastSyncDate)) {
                log.info("[SYNC] New emails detected for user: {}. Triggering Airflow...", userId);
                
                // Trigger Airflow pipeline in background with email and refreshToken
                airflowService.triggerPipelineAsync(email, refreshToken);
                
                // Update last sync date
                userService.updateLastSyncDate(userId, LocalDateTime.now());
            } else {
                log.info("[SYNC] No new emails for user: {}", userId);
            }
        } catch (Exception e) {
            log.warn("[SYNC] Error while checking for new emails: {}", e.getMessage());
            // Do not block response generation in case of error
        }
    }

    private List<Object[]> findSimilarEmailsByUser(String queryEmbedding, String userId) {
        // Search for similar emails ONLY for this specific user
        log.info("[RAG] Searching similar emails for user: {} with TOP_K_RESULTS: {}", userId, TOP_K_RESULTS);
        return emailRepository.findSimilarByCombinedEmbedding(queryEmbedding, userId, TOP_K_RESULTS);
    }

    private String buildConversationHistory(ChatRequest request) {
        StringBuilder conversationHistory = new StringBuilder();
        if (request.getChatHistory() != null && !request.getChatHistory().isEmpty()) {
            for (var msg : request.getChatHistory()) {
                conversationHistory.append(msg.getRole().equals("user") ? "User: " : "Assistant: ")
                        .append(msg.getContent())
                        .append("\n");
            }
        }
        conversationHistory.append("User: ").append(request.getMessage());
        return conversationHistory.toString();
    }

    private String generateEmbedding(String text) {
        // Use Spring AI EmbeddingModel to generate embeddings
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

    private EmailContextResult buildEmailContext(List<Object[]> similarEmails) {
        StringBuilder context = new StringBuilder();
        List<ChatResponse.EmailContext> sources = new ArrayList<>();

        for (Object[] row : similarEmails) {
            String emailId = extractEmailId(row);
            String sender = extractSender(row);
            String subject = extractSubject(row);
            String body = extractBody(row);
            double similarity = extractSimilarity(row);

            // Add to context for LLM
            context.append("\n---\n");
            context.append("Email ID: ").append(emailId).append("\n");
            context.append("Email from: ").append(sender).append("\n");
            context.append("Subject: ").append(subject).append("\n");
            context.append("Content: ").append(truncateText(body)).append("\n");

            // Add to sources list
            sources.add(ChatResponse.EmailContext.builder()
                    .emailId(emailId)
                    .subject(subject)
                    .sender(sender)
                    .similarity(similarity)
                    .build());
        }

        return new EmailContextResult(context.toString(), sources);
    }

    private String extractEmailId(Object[] row) {
        return (String) row[0];
    }

    private String extractSender(Object[] row) {
        return (String) row[1];
    }

    private String extractSubject(Object[] row) {
        return row[5] != null ? (String) row[5] : "";
    }

    private String extractBody(Object[] row) {
        return row[6] != null ? (String) row[6] : "";
    }

    private double extractSimilarity(Object[] row) {
        return row[7] != null ? ((Number) row[7]).doubleValue() : 0.0;
    }

    private ChatResponse buildNoResultsResponse() {
        return ChatResponse.builder()
                .response("I couldn't find any relevant emails to answer your question.")
                .sources(Collections.emptyList())
                .build();
    }

    private ChatResponse buildErrorResponse(Exception e) {
        return ChatResponse.builder()
                .response("Sorry, I encountered an error while processing your request: " + e.getMessage())
                .sources(Collections.emptyList())
                .build();
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
            - Remember the conversation context: if the user refers to "that email" or "this email", understand they are referring to emails mentioned in the previous conversation turns
            - If the email context is empty or no emails are relevant, say "I couldn't find that information in your emails"
            - Be concise but helpful
            - When mentioning an email, reference its sender and subject clearly
            - Only mention emails that are DIRECTLY relevant to the user's question
            - If asked about a specific sender (like Temu, Netflix, etc.), prioritize emails FROM that sender
            - If the user asks to provide/share/give an email that was already mentioned in the conversation, provide the details from the email context
            - Respond in the same language as the user's question
            - Understand contextual references: "that email", "it", "this one", etc. refer to previously mentioned emails
            """.formatted(emailContext);
    }

    private String generateChatResponse(String systemPrompt, String userMessage) {
        // Use Spring AI ChatClient with fluent API to generate response
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    private String truncateText(String text) {
        if (text == null) return "";
        if (text.length() <= ChatServiceImpl.MAX_BODY_LENGTH) return text;
        return text.substring(0, ChatServiceImpl.MAX_BODY_LENGTH) + "...";
    }
}
