package com.example.mini_mindy_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailStatisticsDTO {
    
    // Top cards statistics
    private int totalEmails;
    private int unreadMessages;
    private int importantEmails;
    private int receivedToday;
    
    // Weekly activity (Day -> {count, date})
    private Map<String, Map<String, Object>> weeklyActivity;
    
    // Top senders
    private List<SenderStatDTO> topSenders;
    
    // Additional statistics
    private int sentEmails;
    private int draftEmails;
    private double averageEmailsPerDay;
    private String busiestDay;
    private String mostActiveSender;
}
