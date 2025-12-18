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
}
