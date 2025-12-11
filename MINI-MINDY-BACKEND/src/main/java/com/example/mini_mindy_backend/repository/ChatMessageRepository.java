package com.example.mini_mindy_backend.repository;

import com.example.mini_mindy_backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    // Find messages by session, ordered by creation time
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    
    // Find last N messages for a session (for context)
    List<ChatMessage> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);
}
