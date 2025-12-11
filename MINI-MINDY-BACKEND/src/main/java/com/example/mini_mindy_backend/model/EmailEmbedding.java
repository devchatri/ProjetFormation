package com.example.mini_mindy_backend.model;

import com.example.mini_mindy_backend.config.VectorConverter;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;


@Data
@Entity
@Table(name = "email_embeddings")
public class EmailEmbedding {

    @Id
    private String emailId;

    private String sender;
    private String receiver;
    private OffsetDateTime date;
    private String senderDomain;
    private boolean isImportant;

    // Original text content for RAG retrieval
    @Column(columnDefinition = "TEXT")
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Convert(converter = VectorConverter.class)
    @Column(columnDefinition = "vector(1536)")
    private float[] subjectEmbedding;

    @Convert(converter = VectorConverter.class)
    @Column(columnDefinition = "vector(1536)")
    private float[] bodyEmbedding;

}