package com.example.mini_mindy_backend.service;



import com.example.mini_mindy_backend.model.EmailEmbedding;

import java.util.List;

public interface EmailEmbeddingService {
    void processEmailsFromMinIO();
    List<EmailEmbedding> getAllEmbeddings();
}