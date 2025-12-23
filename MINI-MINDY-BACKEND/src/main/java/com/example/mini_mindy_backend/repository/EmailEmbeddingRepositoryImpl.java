package com.example.mini_mindy_backend.repository;

import com.example.mini_mindy_backend.model.EmailEmbedding;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class EmailEmbeddingRepositoryImpl implements EmailEmbeddingRepositoryCustom {

    private final EntityManager entityManager;
    private static final int EMBEDDING_DIMENSION = 1536;

    @Override
    public List<EmailEmbedding> findByCriteriaFlexible(
            String userId,
            String sender,
            String senderDomain,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Boolean isImportant) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EmailEmbedding> query = cb.createQuery(EmailEmbedding.class);
        Root<EmailEmbedding> root = query.from(EmailEmbedding.class);

        List<Predicate> predicates = new ArrayList<>();

        // Add userId predicate
        predicates.add(cb.equal(root.get("userId"), userId));

        // Add sender predicate
        if (sender != null && !sender.isEmpty()) {
            predicates.add(cb.like(cb.lower(root.get("sender")), 
                    "%" + sender.toLowerCase() + "%"));
        }

        // Add senderDomain predicate
        if (senderDomain != null && !senderDomain.isEmpty()) {
            predicates.add(cb.like(cb.lower(root.get("senderDomain")), 
                    "%" + senderDomain.toLowerCase() + "%"));
        }

        // Add fromDate predicate
        if (fromDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("date"), fromDate));
        }

        // Add toDate predicate
        if (toDate != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("date"), toDate));
        }

        // Add isImportant predicate
        if (isImportant != null) {
            predicates.add(cb.equal(root.get("isImportant"), isImportant));
        }

        // Combine all predicates
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        // Order by date DESC
        query.orderBy(cb.desc(root.get("date")));

        TypedQuery<EmailEmbedding> typedQuery = entityManager.createQuery(query);
        return typedQuery.getResultList();
    }

    @Override
    public double computeSimilarity(float[] subjectEmbedding, float[] bodyEmbedding, String queryEmbeddingJson) {
        float[] query = parseEmbeddingJson(queryEmbeddingJson);

        double subjectSim = cosineSimilarity(subjectEmbedding, query);
        double bodySim = cosineSimilarity(bodyEmbedding, query);

        return 0.3 * subjectSim + 0.7 * bodySim;
    }

    private float[] parseEmbeddingJson(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            // fallback sûr : vecteur de dimension correcte rempli de 0
            return new float[EMBEDDING_DIMENSION]; // <-- mettre ta dimension réelle
        }
        String[] parts = json.replaceAll("[\\[\\]\\s]", "").split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }


    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length == 0 || vec2.length == 0) {
            // fallback sûr : vecteur vide → similarité 0
            return 0.0;
        }
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vector length mismatch: " + vec1.length + " vs " + vec2.length);
        }

        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dot += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2) + 1e-10);
    }

}
