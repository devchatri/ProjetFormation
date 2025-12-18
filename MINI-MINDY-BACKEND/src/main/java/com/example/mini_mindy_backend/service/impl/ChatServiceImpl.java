package com.example.mini_mindy_backend.service.impl;

import com.example.mini_mindy_backend.dto.ChatRequest;
import com.example.mini_mindy_backend.dto.ChatResponse;
import com.example.mini_mindy_backend.dto.EmailSearchCriteria;
import com.example.mini_mindy_backend.model.EmailEmbedding;
import com.example.mini_mindy_backend.repository.EmailEmbeddingRepository;
import com.example.mini_mindy_backend.service.ChatService;
import com.example.mini_mindy_backend.service.GmailService;
import com.example.mini_mindy_backend.service.AirflowService;
import com.example.mini_mindy_backend.service.UserService;
import com.example.mini_mindy_backend.util.EmailContextResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.time.*;
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
            // Check for new emails and trigger Airflow if necessary
            verifyAndTriggerEmailSync(userId);

            // Build conversation history
            String conversationHistory = buildConversationHistory(request);

            // Extraction and filtering with AI
            EmailSearchCriteria criteria = parseCriteriaWithAI(request.getMessage());

            OffsetDateTime fromDate = criteria.getFromDate() != null
                    ? criteria.getFromDate().atStartOfDay().atOffset(ZoneOffset.UTC)
                    : null;

            OffsetDateTime toDate = criteria.getToDate() != null
                    ? criteria.getToDate().atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC)
                    : null;

            List<EmailEmbedding> filteredEmails = emailRepository.findByCriteriaFlexible(
                    userId,
                    criteria.getSender(),
                    criteria.getSenderDomain(),
                    fromDate,
                    toDate,
                    criteria.getIsImportant()
            );

            if (filteredEmails.isEmpty()) {
                String queryEmbedding = generateEmbedding(request.getMessage());
                log.info("[RAG] No emails matched filters, fallback to semantic search");
                List<Object[]> similarEmailRows = findSimilarEmailsByUser(queryEmbedding, userId);
                filteredEmails = convertRowsToEmailEmbedding(similarEmailRows);
            }

            if (filteredEmails.isEmpty()) {
                return buildNoResultsResponse();
            }

            // Build email context
            EmailContextResult contextResult = buildEmailContext(filteredEmails);

            // Generate user response
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

    private List<EmailEmbedding> convertRowsToEmailEmbedding(List<Object[]> rows) {
        List<EmailEmbedding> emails = new ArrayList<>();
        for (Object[] row : rows) {
            EmailEmbedding email = new EmailEmbedding();
            email.setEmailId((String) row[0]);
            email.setSender((String) row[1]);
            email.setReceiver((String) row[2]);
            email.setSenderDomain((String) row[3]);
            email.setSubject((String) row[5]);
            email.setBody((String) row[6]);
            // row[7] is similarity score (Double) - we don't store it in the entity
            emails.add(email);
        }
        return emails;
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

    private EmailContextResult buildEmailContext(List<EmailEmbedding> emails) {
        StringBuilder context = new StringBuilder();
        List<ChatResponse.EmailContext> sources = new ArrayList<>();

        for (int i = 0; i < emails.size(); i++) {
            EmailEmbedding email = emails.get(i);
            // Format date for display
            String dateStr = email.getDate() != null ? email.getDate().toLocalDate().toString() : "Unknown";
            
            // Normalize sender format to "Name <email>" if possible
            String normalizedSender = normalizeSenderFormat(email.getSender());

            // Add to context for LLM with better formatting
            context.append("\n").append("═".repeat(80)).append("\n");
            context.append(String.format("EMAIL #%d\n", i + 1));
            context.append("═".repeat(80)).append("\n");
            context.append(String.format("ID: %s\n", email.getEmailId()));
            context.append(String.format("Date: %s\n", dateStr));
            context.append(String.format("From: %s\n", normalizedSender));
            context.append(String.format("Domain: %s\n", email.getSenderDomain() != null ? email.getSenderDomain() : "N/A"));
            context.append(String.format("Subject: %s\n", email.getSubject() != null ? email.getSubject() : "(No subject)"));
            context.append(String.format("Content:\n%s\n", truncateText(email.getBody())));
            context.append("═".repeat(80)).append("\n");

            // Add to sources list
            sources.add(ChatResponse.EmailContext.builder()
                    .emailId(email.getEmailId())
                    .subject(email.getSubject())
                    .sender(normalizedSender)
                    .date(dateStr)
                    .similarity(0.0)
                    .build());
        }

        return new EmailContextResult(context.toString(), sources);
    }

    private String normalizeSenderFormat(String sender) {
        if (sender == null || sender.trim().isEmpty()) {
            return "Unknown Sender";
        }
        
        sender = sender.trim();
        
        // If already in "Name <email>" format, return as is
        if (sender.contains("<") && sender.contains(">")) {
            return sender;
        }
        
        // If it's just an email, try to extract name from email
        if (sender.contains("@")) {
            // Extract username part before @
            String username = sender.substring(0, sender.indexOf("@"));
            // Convert common formats to readable names
            String readableName = username.replace(".", " ").replace("_", " ");
            // Capitalize first letter of each word
            String[] words = readableName.split(" ");
            for (int i = 0; i < words.length; i++) {
                if (words[i].length() > 0) {
                    words[i] = words[i].substring(0, 1).toUpperCase() + 
                              (words[i].length() > 1 ? words[i].substring(1).toLowerCase() : "");
                }
            }
            readableName = String.join(" ", words);
            return readableName + " <" + sender + ">";
        }
        
        // If it's just a name without email, return as is
        return sender;
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
        
        CRITICAL LANGUAGE RULE:
        Always respond in the EXACT same language as the user's question.

        You have access to the user's emails. Use the following email context to answer their questions:

        === EMAIL CONTEXT ===
        %s
        === END CONTEXT ===

        Instructions:
        - Be concise but helpful
        - When mentioning an email, reference its sender and subject clearly
        - Only mention emails that are DIRECTLY relevant to the user's question
        - If asked about a specific sender (e.g. Temu, Netflix, Amazon), prioritize emails FROM that sender
        - If the user asks to provide, share, or give an email already mentioned in the conversation,
          provide the details from the email context
        - Understand contextual references such as:
          "that email", "it", "this one", etc. → they refer to previously mentioned emails
        - NEVER invent emails or content
        - If the email context does not contain the answer, say:
          "I couldn't find that information in your emails."

        - When listing emails, use this EXACT format:
          1. 10:47 (Today) - Alae Haddad <alaehaddad205@gmail.com>
             📌 Subject: "Test2"
             🎯 Content: Test2
          2. 10:47 (Yesterday) - Alae Haddad <alaehaddad205@gmail.com>
             📌 Subject: "Test"
             🎯 Content: Test
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


    private EmailSearchCriteria parseCriteriaWithAI(String userMessage) {
        // Calculate dynamic dates for today and yesterday
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate dayBeforeYesterday = today.minusDays(2);
        String todayStr = today.toString();
        String yesterdayStr = yesterday.toString();
        String dayBeforeYesterdayStr = dayBeforeYesterday.toString();
        
        String prompt = """
        Extract email search filters from this user request. Return ONLY valid JSON (no markdown, no backticks):
        {
          "sender": "email or name or null",
          "senderDomain": "domain or null",
          "fromDate": "YYYY-MM-DD or null",
          "toDate": "YYYY-MM-DD or null",
          "isImportant": true/false or null
        }
        
        CRITICAL RULES FOR DATE EXTRACTION:
        - Today's date is: %s
        - Yesterday's date is: %s
        - Day before yesterday is: %s
        
        DATE RANGE HANDLING (PRIORITY):
        - "aujourd'hui et hier et avant-hier" → {"fromDate": "%s", "toDate": "%s"}
        - "aujourd'hui et hier" → {"fromDate": "%s", "toDate": "%s"}
        - "hier et avant-hier" → {"fromDate": "%s", "toDate": "%s"}
        
        SINGLE DATE HANDLING:
        - "aujourd'hui" ONLY → {"fromDate": "%s", "toDate": "%s"}
        - "hier" ONLY → {"fromDate": "%s", "toDate": "%s"}
        
        User request: "%s"
        """.formatted(todayStr, yesterdayStr, dayBeforeYesterdayStr, dayBeforeYesterdayStr, todayStr, yesterdayStr, todayStr, dayBeforeYesterdayStr, yesterdayStr, todayStr, todayStr, yesterdayStr, yesterdayStr, userMessage);

        log.debug("[AI] Parsing criteria with prompt containing today={}, yesterday={}", todayStr, yesterdayStr);

        String response = chatClient.prompt()
                .system("You are an AI that extracts structured email search filters from natural language requests. Return ONLY valid JSON, NO markdown formatting. Always follow the CRITICAL RULES. Today is " + todayStr + ".")
                .user(prompt)
                .call()
                .content();

        log.debug("[AI] LLM Response: {}", response);

        // Clean up markdown formatting if present
        response = response.replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try {
            EmailSearchCriteria criteria = mapper.readValue(response, EmailSearchCriteria.class);
            log.info("[AI] Extracted criteria: sender={}, domain={}, fromDate={}, toDate={}, important={}", 
                    criteria.getSender(), criteria.getSenderDomain(), criteria.getFromDate(), 
                    criteria.getToDate(), criteria.getIsImportant());
            
            // Log if we successfully detected a date filter
            if (criteria.getFromDate() != null || criteria.getToDate() != null) {
                log.info("[AI] Date filter detected: from={} to={}", criteria.getFromDate(), criteria.getToDate());
            }
            
            return criteria;
        } catch (Exception e) {
            log.warn("[AI] Filter extraction failed: {} - Raw response: {}", e.getMessage(), response);
            log.warn("[AI] User message was: {}", userMessage);
            return new EmailSearchCriteria(); // fallback empty
        }
    }


}
