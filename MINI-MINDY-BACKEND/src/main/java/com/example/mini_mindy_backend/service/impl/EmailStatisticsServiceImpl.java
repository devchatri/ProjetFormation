package com.example.mini_mindy_backend.service.impl;

import com.example.mini_mindy_backend.dto.EmailStatisticsDTO;
import com.example.mini_mindy_backend.dto.SenderStatDTO;
import com.example.mini_mindy_backend.model.User;
import com.example.mini_mindy_backend.repository.UserRepository;
import com.example.mini_mindy_backend.service.EmailStatisticsService;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailStatisticsServiceImpl implements EmailStatisticsService {

    private static final String APPLICATION_NAME = "Mini Mindy";
    private static final String USER_ID = "me";
    private final UserRepository userRepository;

    private Gmail getGmailServiceForUser(String email) throws Exception {
        GoogleClientSecrets secrets = GoogleClientSecrets.load(
                JacksonFactory.getDefaultInstance(),
                new FileReader("src/main/resources/credentials.json")
        );

        User user = userRepository.findByEmail(email).orElseThrow();
        String refreshToken = user.getGoogleRefreshToken();

        TokenResponse token = new TokenResponse();
        token.setRefreshToken(refreshToken);

        Credential credential = new Credential
                .Builder(BearerToken.authorizationHeaderAccessMethod())
                .setJsonFactory(JacksonFactory.getDefaultInstance())
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setClientAuthentication(new BasicAuthentication(
                        secrets.getDetails().getClientId(),
                        secrets.getDetails().getClientSecret()
                ))
                .setTokenServerEncodedUrl("https://oauth2.googleapis.com/token")
                .build();

        credential.setRefreshToken(refreshToken);

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
        ).setApplicationName(APPLICATION_NAME).build();
    }

    @Override
    public EmailStatisticsDTO getEmailStatistics(String userEmail) throws Exception {
        Gmail service = getGmailServiceForUser(userEmail);
        
        // Get today's date range (start and end of today)
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();
        long todayStartMillis = todayStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long todayEndMillis = todayEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        // Statistics counters (all for today only)
        int totalEmails = 0;
        int unreadMessages = 0;
        int importantEmails = 0;
        int receivedToday = 0;
        int sentEmails = 0;
        int draftEmails = 0;
        
        // Weekly activity: Map<Day, Map<count, date>>
        Map<String, Map<String, Object>> weeklyActivity = new LinkedHashMap<>();
        Map<String, Integer> senderCounts = new HashMap<>();
        
        // Calculer le début de la semaine (lundi)
        DayOfWeek todayDow = today.getDayOfWeek();
        String todayName = todayDow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        int daysFromMonday = todayDow.getValue() - 1; // Lundi=0, Dimanche=6
        LocalDate weekStart = today.minusDays(daysFromMonday);
        
        // Initialiser la map avec les 7 jours de la semaine
        String[] weekDays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        for (int i = 0; i < weekDays.length; i++) {
            LocalDate date = weekStart.plusDays(i);
            String dateStr = date.format(dateFormatter);
            Map<String, Object> dayInfo = new HashMap<>();
            dayInfo.put("count", 0);
            dayInfo.put("date", dateStr);
            weeklyActivity.put(weekDays[i], dayInfo);
        }

        try {
            // Requête Gmail pour tous les emails reçus depuis le début de la semaine
            long weekStartMillis = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long weekStartSeconds = weekStartMillis / 1000;
            String query = "in:inbox after:" + weekStartSeconds;
            
            // Get inbox messages from the beginning of the week
            ListMessagesResponse inboxResponse = service.users().messages()
                    .list(USER_ID)
                    .setQ(query)
                    .setMaxResults(500L)
                    .execute();

            if (inboxResponse.getMessages() != null) {
                for (Message msg : inboxResponse.getMessages()) {
                    Message fullMessage = service.users().messages().get(USER_ID, msg.getId())
                            .setFormat("metadata")
                            .setMetadataHeaders(Arrays.asList("From", "Date", "Subject"))
                            .execute();
                    
                    long messageTime = fullMessage.getInternalDate();
                    LocalDate msgDate = Instant.ofEpochMilli(messageTime).atZone(ZoneId.systemDefault()).toLocalDate();
                    String dayOfWeek = msgDate.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                    
                    // On ne compte que les emails jusqu'à aujourd'hui inclus
                    if (!msgDate.isAfter(today)) {
                        // Mettre à jour weeklyActivity pour le bon jour
                        if (weeklyActivity.containsKey(dayOfWeek)) {
                            Map<String, Object> dayInfo = weeklyActivity.get(dayOfWeek);
                            int prev = (int) dayInfo.get("count");
                            dayInfo.put("count", prev + 1);
                        }
                        
                        // Pour les stats "aujourd'hui" uniquement
                        if (msgDate.isEqual(today)) {
                            totalEmails++;
                            receivedToday++;
                            
                            // Check if unread (aujourd'hui uniquement)
                            if (fullMessage.getLabelIds() != null && fullMessage.getLabelIds().contains("UNREAD")) {
                                unreadMessages++;
                            }
                            
                            // Check if important (aujourd'hui uniquement)
                            if (fullMessage.getLabelIds() != null && fullMessage.getLabelIds().contains("IMPORTANT")) {
                                importantEmails++;
                            }
                        }
                        
                        // Extract sender information (pour toute la semaine)
                        MessagePartHeader fromHeader = fullMessage.getPayload().getHeaders().stream()
                                .filter(h -> h.getName().equals("From"))
                                .findFirst()
                                .orElse(null);
                        
                        if (fromHeader != null) {
                            String from = fromHeader.getValue();
                            String senderEmail = extractEmail(from);
                            senderCounts.merge(senderEmail, 1, Integer::sum);
                        }
                    }
                }
            }

            // Get sent emails today
            long todayStartSeconds = todayStartMillis / 1000;
            String sentQuery = "in:sent after:" + todayStartSeconds;
            ListMessagesResponse sentResponse = service.users().messages()
                    .list(USER_ID)
                    .setQ(sentQuery)
                    .setMaxResults(100L)
                    .execute();
            
            if (sentResponse.getMessages() != null) {
                for (Message msg : sentResponse.getMessages()) {
                    long messageTime = service.users().messages().get(USER_ID, msg.getId())
                            .setFormat("minimal")
                            .execute()
                            .getInternalDate();
                    
                    if (messageTime >= todayStartMillis && messageTime < todayEndMillis) {
                        sentEmails++;
                    }
                }
            }

            // Get all drafts (drafts don't have a date filter)
            ListDraftsResponse draftsResponse = service.users().drafts()
                    .list(USER_ID)
                    .execute();
            
            if (draftsResponse.getDrafts() != null) {
                draftEmails = draftsResponse.getDrafts().size();
            }

        } catch (Exception e) {
            log.error("Error fetching email statistics for today", e);
            throw e;
        }

        // Calculate top senders (from today only)
        List<SenderStatDTO> topSenders = senderCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .map(entry -> {
                    String email = entry.getKey();
                    String name = extractName(email);
                    return SenderStatDTO.builder()
                            .senderName(name)
                            .senderEmail(email)
                            .emailCount(entry.getValue())
                            .build();
                })
                .collect(Collectors.toList());

        // Calculate additional statistics (for today only)
        double averageEmailsPerDay = totalEmails; // Today's average is just today's count
        
        String busiestDay = todayName; // Only today in the data
        
        String mostActiveSender = topSenders.isEmpty() ? "N/A" : topSenders.get(0).getSenderName();

        return EmailStatisticsDTO.builder()
                .totalEmails(totalEmails)
                .unreadMessages(unreadMessages)
                .importantEmails(importantEmails)
                .receivedToday(receivedToday)
                .weeklyActivity(weeklyActivity)
                .topSenders(topSenders)
                .sentEmails(sentEmails)
                .draftEmails(draftEmails)
                .averageEmailsPerDay(averageEmailsPerDay)
                .busiestDay(busiestDay)
                .mostActiveSender(mostActiveSender)
                .build();
    }

    private String extractEmail(String fromHeader) {
        // Extract email from "Name <email@domain.com>" format
        if (fromHeader.contains("<") && fromHeader.contains(">")) {
            int start = fromHeader.indexOf("<") + 1;
            int end = fromHeader.indexOf(">");
            return fromHeader.substring(start, end);
        }
        return fromHeader;
    }

    private String extractName(String email) {
        // Extract name from email or use email prefix
        if (email.contains("@")) {
            String[] parts = email.split("@");
            String domain = parts[1];
            
            // Check if it's a common domain (LinkedIn, Google, Amazon)
            if (domain.toLowerCase().contains("linkedin")) {
                return "LinkedIn";
            } else if (domain.toLowerCase().contains("google")) {
                return "Google";
            } else if (domain.toLowerCase().contains("amazon")) {
                return "Amazon";
            }
            
            // Return capitalized domain name
            String domainName = domain.split("\\.")[0];
            return domainName.substring(0, 1).toUpperCase() + domainName.substring(1);
        }
        return email;
    }
}
