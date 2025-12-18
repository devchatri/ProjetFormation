package com.example.mini_mindy_backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class EmailSearchCriteria {
    private String sender;
    private String senderDomain;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Boolean isImportant;
}
