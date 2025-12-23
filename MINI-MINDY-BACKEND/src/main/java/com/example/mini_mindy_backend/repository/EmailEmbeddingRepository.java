package com.example.mini_mindy_backend.repository;


import com.example.mini_mindy_backend.model.EmailEmbedding;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface EmailEmbeddingRepository extends JpaRepository<EmailEmbedding, String>, EmailEmbeddingRepositoryCustom {


    @Modifying
    @Transactional
    @Query(value = "INSERT INTO email_embeddings (email_id, user_id, sender, receiver, sender_domain, is_important, subject, body, date, subject_embedding, body_embedding) " +
            "VALUES (:emailId, :userId, :sender, :receiver, :senderDomain, :isImportant, :subject, :body, :date, " +
            "CAST(:subjectEmbedding AS vector), CAST(:bodyEmbedding AS vector))",
            nativeQuery = true)
    void insertWithEmbeddingsAndUserId(
            @Param("emailId") String emailId,
            @Param("userId") String userId,
            @Param("sender") String sender,
            @Param("receiver") String receiver,
            @Param("senderDomain") String senderDomain,
            @Param("isImportant") boolean isImportant,
            @Param("subject") String subject,
            @Param("body") String body,
            @Param("date") java.time.OffsetDateTime date,
            @Param("subjectEmbedding") String subjectEmbedding,
            @Param("bodyEmbedding") String bodyEmbedding
    );

    /**
     * Search similar emails using combined subject + body similarity
     * Filters results to only include emails belonging to the specified user
     * Returns: email_id, sender, receiver, sender_domain, is_important, subject, body, similarity
     */
    @Query(value = "SELECT e.email_id, e.sender, e.receiver, e.sender_domain, e.is_important, e.subject, e.body, e.date," +
            "(0.3 * (1 - (e.subject_embedding <=> CAST(:queryEmbedding AS vector))) + " +
            " 0.7 * (1 - (e.body_embedding <=> CAST(:queryEmbedding AS vector)))) as similarity " +
            "FROM email_embeddings e " +
            "WHERE e.user_id = :userId " +
            "ORDER BY similarity DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findSimilarByCombinedEmbedding(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("userId") String userId,
            @Param("limit") int limit
    );

    @Query("""
                SELECT e
                FROM EmailEmbedding e
                WHERE e.userId = :userId
                ORDER BY e.date DESC
            """)
    List<EmailEmbedding> findLastNByUser(
            @Param("userId") String userId,
            Pageable pageable
    );

}