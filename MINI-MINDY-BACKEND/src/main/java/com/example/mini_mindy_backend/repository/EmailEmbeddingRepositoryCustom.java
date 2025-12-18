package com.example.mini_mindy_backend.repository;

import com.example.mini_mindy_backend.model.EmailEmbedding;
import java.time.OffsetDateTime;
import java.util.List;

public interface EmailEmbeddingRepositoryCustom {
    
    List<EmailEmbedding> findByCriteriaFlexible(
            String userId,
            String sender,
            String senderDomain,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Boolean isImportant);
}
