package com.example.mini_mindy_backend.repository;

import com.example.mini_mindy_backend.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    
    // Find sessions by user, ordered by most recent
    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(String userId);
    
    // Find the most recent session for a user
    Optional<ChatSession> findFirstByUserIdOrderByUpdatedAtDesc(String userId);
    
    // Find session with messages loaded
    @Query("SELECT s FROM ChatSession s LEFT JOIN FETCH s.messages WHERE s.id = :sessionId")
    Optional<ChatSession> findByIdWithMessages(@Param("sessionId") Long sessionId);
}
