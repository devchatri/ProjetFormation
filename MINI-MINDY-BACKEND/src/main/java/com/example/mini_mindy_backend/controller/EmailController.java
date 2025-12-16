package com.example.mini_mindy_backend.controller;

import com.example.mini_mindy_backend.dto.EmailDTO;
import com.example.mini_mindy_backend.model.User;
import com.example.mini_mindy_backend.repository.UserRepository;
import com.example.mini_mindy_backend.service.GmailService;
import com.example.mini_mindy_backend.util.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

    @Autowired
    private GmailService gmailService;

    @Autowired
    private JwtService jwtService;

    @GetMapping("/recent")
    public ResponseEntity<?> getRecentEmails(@RequestHeader("Authorization") String authHeader) {
        try {
            String jwt = authHeader.replace("Bearer ", "");
            String email = jwtService.extractEmail(jwt);
            List<EmailDTO> emails = gmailService.getRecentEmailsForUser(email, 10);
            return ResponseEntity.ok(emails);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erreur: " + e.getMessage());
        }
    }
}
