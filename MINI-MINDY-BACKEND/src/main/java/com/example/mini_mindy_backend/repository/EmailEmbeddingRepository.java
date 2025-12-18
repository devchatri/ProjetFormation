package com.example.mini_mindy_backend.repository;


import com.example.mini_mindy_backend.model.EmailEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.time.LocalDate;

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

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO email_embeddings (email_id, sender, receiver, sender_domain, is_important, subject, body, date, subject_embedding, body_embedding) " +
            "VALUES (:emailId, :sender, :receiver, :senderDomain, :isImportant, :subject, :body, :date, " +
            "CAST(:subjectEmbedding AS vector), CAST(:bodyEmbedding AS vector))",
            nativeQuery = true)
    void insertWithEmbeddings(
            @Param("emailId") String emailId,
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
     * Search similar emails using pgvector cosine similarity on body_embedding
     * Returns top K most similar emails to the query embedding
     */
    @Query(value = "SELECT e.email_id, e.sender, e.receiver, e.sender_domain, e.is_important, e.subject, e.body, " +
            "1 - (e.body_embedding <=> CAST(:queryEmbedding AS vector)) as similarity " +
            "FROM email_embeddings e " +
            "ORDER BY e.body_embedding <=> CAST(:queryEmbedding AS vector) " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findSimilarByBodyEmbedding(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit
    );

    /**
     * Search similar emails using combined subject + body similarity
     * Filters results to only include emails belonging to the specified user
     * Returns: email_id, sender, receiver, sender_domain, is_important, subject, body, similarity
     */
    @Query(value = "SELECT e.email_id, e.sender, e.receiver, e.sender_domain, e.is_important, e.subject, e.body, " +
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

    /**
     * Find emails by date for the specified user
     * Used for date-based queries like "emails from today", "emails from yesterday", etc.
     * Returns: email_id, sender, receiver, sender_domain, is_important, subject, body, 1.0 (as similarity)
     */
    @Query(value = "SELECT e.email_id, e.sender, e.receiver, e.sender_domain, e.is_important, e.subject, e.body, " +
            "CAST(1.0 AS FLOAT) as similarity " +
            "FROM email_embeddings e " +
            "WHERE e.user_id = :userId " +
            "AND DATE(e.date) = :date " +
            "ORDER BY e.date DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findEmailsByUserAndDate(
            @Param("userId") String userId,
            @Param("date") LocalDate date,
            @Param("limit") int limit
    );

    /**
     * Find emails by sender for the specified user
     * Used for sender-based queries like "emails from Alae", etc.
     * Returns: email_id, sender, receiver, sender_domain, is_important, subject, body, 1.0 (as similarity)
     */
    @Query(value = "SELECT e.email_id, e.sender, e.receiver, e.sender_domain, e.is_important, e.subject, e.body, " +
            "CAST(1.0 AS FLOAT) as similarity " +
            "FROM email_embeddings e " +
            "WHERE e.user_id = :userId " +
            "AND LOWER(e.sender) = LOWER(:sender) " +
            "ORDER BY e.date DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findEmailsByUserAndSender(
            @Param("userId") String userId,
            @Param("sender") String sender,
            @Param("limit") int limit
    );

    /**
     * Find emails by date and sender for the specified user
     * Used for queries like "emails from Alae today", etc.
     * Returns: email_id, sender, receiver, sender_domain, is_important, subject, body, 1.0 (as similarity)
     */
    @Query(value = "SELECT e.email_id, e.sender, e.receiver, e.sender_domain, e.is_important, e.subject, e.body, " +
            "CAST(1.0 AS FLOAT) as similarity " +
            "FROM email_embeddings e " +
            "WHERE e.user_id = :userId " +
            "AND DATE(e.date) = :date " +
            "AND LOWER(e.sender) = LOWER(:sender) " +
            "ORDER BY e.date DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findEmailsByUserAndDateAndSender(
            @Param("userId") String userId,
            @Param("date") LocalDate date,
            @Param("sender") String sender,
            @Param("limit") int limit
    );


    @Query(value = """
    SELECT e.email_id, e.sender, e.receiver, e.sender_domain, e.is_important, e.subject, e.body, 
            e.date, e.subject_embedding, e.body_embedding, e.user_id
    FROM email_embeddings e
    WHERE e.user_id = :userId
      AND (:sender IS NULL OR LOWER(CAST(e.sender AS TEXT)) LIKE LOWER(CONCAT('%', :sender, '%')))
      AND (:senderDomain IS NULL OR LOWER(CAST(e.sender_domain AS TEXT)) LIKE LOWER(CONCAT('%', :senderDomain, '%')))
      AND (:fromDate IS NULL OR e.date >= CAST(:fromDate AS TIMESTAMP WITH TIME ZONE))
      AND (:toDate IS NULL OR e.date <= CAST(:toDate AS TIMESTAMP WITH TIME ZONE))
      AND (:isImportant IS NULL OR e.is_important = :isImportant)
    ORDER BY e.date DESC
    """, nativeQuery = true)
    List<EmailEmbedding> findByCriteria(
            @Param("userId") String userId,
            @Param("sender") String sender,
            @Param("senderDomain") String senderDomain,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            @Param("isImportant") Boolean isImportant
    );


}