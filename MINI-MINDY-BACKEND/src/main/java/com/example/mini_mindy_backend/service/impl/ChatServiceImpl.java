package com.example.mini_mindy_backend.service.impl;

import com.example.mini_mindy_backend.dto.ChatRequest;
import com.example.mini_mindy_backend.dto.ChatResponse;
import com.example.mini_mindy_backend.dto.EmailSearchCriteria;
import com.example.mini_mindy_backend.model.EmailEmbedding;
import com.example.mini_mindy_backend.model.enums.RetrievalStrategy;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.annotation.PostConstruct;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int TOP_K_RESULTS = 5;
    private static final int GLOBAL_CONTEXT_SIZE = 20;
    private static final int MAX_BODY_LENGTH = 500;
    private static final double SIMILARITY_THRESHOLD = 0.1;

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

        log.info("[RAG] User={} Question={}", userId, request.getMessage());

        verifyAndTriggerEmailSync(userId);

        String conversationHistory = buildConversationHistory(request);

        RetrievalStrategy strategy = decideStrategyWithAI(request.getMessage());
        log.info("[RAG] Strategy selected: {}", strategy);

        List<EmailEmbedding> emails = switch (strategy) {

            case FILTER_FIRST -> filterFirst(userId, request.getMessage());

            case HYBRID -> hybridSearch(userId, request.getMessage());

            case GLOBAL_CONTEXT -> {
                List<EmailEmbedding> recent = emailRepository.findLastNByUser(
                                userId,
                                PageRequest.of(0, GLOBAL_CONTEXT_SIZE)
                        ).stream()
                        .peek(e -> e.setSimilarity(1.0))
                        .toList();

                if (recent.isEmpty()) {
                    log.info("[RAG] GLOBAL_CONTEXT returned empty, falling back to SEMANTIC_FIRST");
                    yield semanticSearch(userId, request.getMessage());
                }
                yield recent;
            }

            case SEMANTIC_FIRST -> semanticSearch(userId, request.getMessage());
        };

        if (emails.isEmpty()) {
            return buildNoResultsResponse(request.getMessage());
        }

        EmailContextResult context = buildEmailContext(emails);

        String systemPrompt = buildSystemPrompt(
                context.getContext(),
                strategy
        );

        String response = generateChatResponse(systemPrompt, conversationHistory);

        return ChatResponse.builder()
                .response(response)
                .sources(context.getSources())
                .build();
    }

    /* ===================== STRATEGIES ===================== */

    private List<EmailEmbedding> filterFirst(String userId, String message) {
        EmailSearchCriteria criteria = parseCriteriaWithAI(message);
        log.info("[FILTER_FIRST] Criteria: {}", criteria);

        OffsetDateTime from = criteria.getFromDate() != null ?
                criteria.getFromDate().atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime to = criteria.getToDate() != null ?
                criteria.getToDate().atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC) : null;

        List<EmailEmbedding> result = emailRepository.findByCriteriaFlexible(userId,
                criteria.getSender(),
                criteria.getSenderDomain(),
                from,
                to,
                criteria.getIsImportant());

        log.info("[FILTER_RESULT] result: {}", result.toArray().length);

        boolean hasExplicitFilter = criteria.getFromDate() != null
                || criteria.getToDate() != null
                || criteria.getSender() != null
                || criteria.getSenderDomain() != null
                || criteria.getIsImportant() != null;

        if (hasExplicitFilter && result.isEmpty()) {
            return Collections.emptyList();
        }

        return result.isEmpty() ? semanticSearch(userId, message) : result;
    }

    private List<EmailEmbedding> hybridSearch(String userId, String message) {

        List<EmailEmbedding> filtered = filterFirst(userId, message);

        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        String embedding = generateEmbedding(message);

        filtered.forEach(e -> {
            double sim = emailRepository.computeSimilarity(
                    e.getSubjectEmbedding(),
                    e.getBodyEmbedding(),
                    embedding
            );
            e.setSimilarity(sim);
        });

        return filtered.stream()
                .filter(e -> e.getSimilarity() >= SIMILARITY_THRESHOLD)
                .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
                .limit(TOP_K_RESULTS)
                .toList();
    }

    private List<EmailEmbedding> semanticSearch(String userId, String message) {

        String embedding = generateEmbedding(message);
        List<Object[]> rows =
                emailRepository.findSimilarByCombinedEmbedding(
                        embedding,
                        userId,
                        TOP_K_RESULTS
                );

        return convertRowsToEmailEmbedding(rows);
    }

    /* ===================== STRATEGY DECISION ===================== */

    private RetrievalStrategy decideStrategyWithAI(String userMessage) {

        String prompt = """
                Decide the best email retrieval strategy for the user question.
                
                Strategies:
                - FILTER_FIRST: clear sender, date, domain, or explicit filters
                - SEMANTIC_FIRST: topic-based, meaning-based, implicit questions
                - HYBRID: filters + topic combined
                - GLOBAL_CONTEXT: general overview, summary, counts, trends, analytics
                
                Return ONLY one of:
                FILTER_FIRST | SEMANTIC_FIRST | HYBRID | GLOBAL_CONTEXT
                
                User question: "%s"
                """.formatted(userMessage);

        String response = Objects.requireNonNull(chatClient.prompt()
                        .system("You are an AI that selects the best retrieval strategy. Return ONLY the strategy name.")
                        .user(prompt)
                        .call()
                        .content())
                .trim();

        try {
            return RetrievalStrategy.valueOf(response);
        } catch (Exception e) {
            log.warn("[RAG] Unknown strategy '{}', defaulting to SEMANTIC_FIRST", response);
            return RetrievalStrategy.SEMANTIC_FIRST;
        }
    }

    /* ===================== CONTEXT ===================== */

    private EmailContextResult buildEmailContext(List<EmailEmbedding> emails) {

        StringBuilder context = new StringBuilder();
        List<ChatResponse.EmailContext> sources = new ArrayList<>();

        for (int i = 0; i < emails.size(); i++) {

            EmailEmbedding email = emails.get(i);

            String dateTime = email.getDate() != null
                    ? email.getDate().toLocalDate() + " " + email.getDate().toLocalTime().withSecond(0).withNano(0)
                    : "Unknown";

            String sender = normalizeSender(email.getSender());

            context.append("\n══════════════════════════════════════════════════\n");
            context.append("EMAIL #").append(i + 1).append("\n");
            context.append("Date: ").append(dateTime).append("\n");
            context.append("From: ").append(sender).append("\n");
            context.append("Subject: ").append(email.getSubject()).append("\n");
            context.append("Content:\n").append(truncate(email.getBody())).append("\n");

            sources.add(ChatResponse.EmailContext.builder()
                    .emailId(email.getEmailId())
                    .sender(sender)
                    .subject(email.getSubject())
                    .date(dateTime)
                    .similarity(email.getSimilarity())
                    .build());
        }

        return new EmailContextResult(context.toString(), sources);
    }

    /* ===================== PROMPT ===================== */

    private String buildSystemPrompt(String emailContext, RetrievalStrategy strategy) {

        return """
                You are Mini-Mindy, an AI email assistant.
                
                CRITICAL LANGUAGE RULE:
                Always respond in the SAME language as the user.
                
                RETRIEVAL STRATEGY:
                %s
                
                RULES:
                - Use ONLY the provided emails
                - Do NOT invent content
                - You may analyze, summarize, or infer based on the emails
                - Say "I couldn't find that information in your emails"
                  ONLY if emails are completely unrelated
                - Only say "I couldn't find..." if there are truly zero emails
                - When listing emails, use this EXACT format:
                    1. ** date ** - sender <email>
                       📌 Subject:
                       🎯 Content:
                
                === EMAIL CONTEXT ===
                %s
                === END CONTEXT ===
                """.formatted(strategy.name(), emailContext);
    }

    /* ===================== HELPERS ===================== */

    private String generateEmbedding(String text) {
        float[] vector = embeddingModel.embed(text);
        if (vector == null || vector.length == 0) {
            log.warn("[EMBEDDING] Received null or empty vector for text: {}", text);
            return "[]"; // fallback sûr
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        return sb.append("]").toString();
    }

    private List<EmailEmbedding> convertRowsToEmailEmbedding(List<Object[]> rows) {
        List<EmailEmbedding> emails = new ArrayList<>();
        for (Object[] r : rows) {
            EmailEmbedding e = new EmailEmbedding();
            e.setEmailId((String) r[0]);
            e.setSender((String) r[1]);
            e.setReceiver((String) r[2]);
            e.setSenderDomain((String) r[3]);
            e.setImportant((Boolean) r[4]);
            e.setSubject((String) r[5]);
            e.setBody((String) r[6]);
            if (r[7] != null) {
                Instant instant = (Instant) r[7];
                e.setDate(instant.atOffset(ZoneOffset.UTC));
            }
            e.setSimilarity(((Number) r[8]).doubleValue());

            emails.add(e);
        }
        return emails;
    }


    private String normalizeSender(String sender) {
        if (sender == null) return "Unknown";
        if (sender.contains("<")) return sender;
        if (sender.contains("@")) return sender.substring(0, sender.indexOf("@")) + " <" + sender + ">";
        return sender;
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_BODY_LENGTH
                ? text.substring(0, MAX_BODY_LENGTH) + "..."
                : text;
    }

    private String buildConversationHistory(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getChatHistory() != null) {
            request.getChatHistory().forEach(m ->
                    sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n")
            );
        }
        sb.append("User: ").append(request.getMessage());
        return sb.toString();
    }

    private ChatResponse buildNoResultsResponse(String userMessage) {

        // Create a system prompt instructing the AI to reply naturally in the same language
        String systemPrompt = """
                You are Mini-Mindy, an AI email assistant.
                The user asked: "%s"
                There are no emails that match this query.
                Respond naturally and clearly in the SAME language as the user's question.
                """.formatted(userMessage);

        String response = Objects.requireNonNull(chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .call()
                        .content())
                .trim();

        return ChatResponse.builder()
                .response(response)
                .sources(Collections.emptyList())
                .build();
    }

    private String generateChatResponse(String systemPrompt, String userMessage) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    private void verifyAndTriggerEmailSync(String userId) {
        try {
            String email = userService.getEmailByUserId(userId);
            String refresh = userService.getRefreshTokenByUserId(userId);
            LocalDateTime lastSync = userService.getLastSyncDate(userId);

            if (email == null || refresh == null) return;

            if (lastSync == null || gmailService.hasNewEmails(userId, lastSync)) {
                airflowService.triggerPipelineAsync(email, refresh);
                userService.updateLastSyncDate(userId, LocalDateTime.now());
            }
        } catch (Exception e) {
            log.warn("[SYNC] {}", e.getMessage());
        }
    }

    private EmailSearchCriteria parseCriteriaWithAI(String userMessage) {
        LocalDate today = LocalDate.now();

        String prompt = """
            Extract email search filters from this user request. Return ONLY valid JSON (no markdown):
            {
              "sender": "email or name or null",
              "senderDomain": "domain or null",
              "fromDate": "YYYY-MM-DD or null",
              "toDate": "YYYY-MM-DD or null",
              "isImportant": true/false or null
            }

            IMPORTANT:
            - The user may request relative periods like "today", "yesterday", "this week", "last week", "this month", "last month"
            - Return absolute dates for all periods (YYYY-MM-DD)
            - If no date is specified, return null for fromDate and toDate

            User request: "%s"
            """.formatted(userMessage);

        try {
            String response = chatClient.prompt()
                    .system("You are an AI that extracts structured email search filters. Return ONLY JSON with absolute dates.")
                    .user(prompt)
                    .call()
                    .content();

            if (response == null) response = "{}";

            response = response.replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            EmailSearchCriteria criteria = mapper.readValue(response, EmailSearchCriteria.class);

            // fallback si l'AI renvoie null
            if (criteria.getFromDate() == null && criteria.getToDate() == null) {
                criteria.setFromDate(null);
                criteria.setToDate(null);
            }

            return criteria;

        } catch (Exception e) {
            log.warn("[AI] Failed to parse criteria: {} - Raw response: {}", e.getMessage(), userMessage);
            return new EmailSearchCriteria(); // fallback vide
        }
    }

}