package com.example.mini_mindy_backend.dto;

import lombok.Data;

@Data
public class EmailDTO {
    private String id;
    private String subject;
    private String from;
    private String snippet;
    private String date;
    private String body;
}
