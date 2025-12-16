package com.example.mini_mindy_backend.service;



import com.example.mini_mindy_backend.model.EmailEmbedding;

import java.util.List;

public interface EmailEmbeddingService {
    /**
     * Process emails from MinIO for a specific user
     * @param userId User ID to process embeddings for
     */
    void processEmailsFromMinIO(String userId);
    
    List<EmailEmbedding> getAllEmbeddings();
}