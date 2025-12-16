package com.example.mini_mindy_backend.util;

import com.example.mini_mindy_backend.dto.ChatResponse;

import java.util.List;

public class EmailContextResult {
    private final String context;
    private final List<ChatResponse.EmailContext> sources;

    public EmailContextResult(String context, List<ChatResponse.EmailContext> sources) {
        this.context = context;
        this.sources = sources;
    }

    public String getContext() {
        return context;
    }

    public List<ChatResponse.EmailContext> getSources() {
        return sources;
    }
}
