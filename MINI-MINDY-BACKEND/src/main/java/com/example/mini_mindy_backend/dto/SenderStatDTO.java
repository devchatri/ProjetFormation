package com.example.mini_mindy_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SenderStatDTO {
    private String senderName;
    private String senderEmail;
    private int emailCount;
}
