package com.example.mini_mindy_backend.controller;

import com.example.mini_mindy_backend.dto.EmailDTO;
import com.example.mini_mindy_backend.service.GmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

    @Autowired
    private GmailService gmailService;

    @GetMapping("/recent")
    public ResponseEntity<?> getRecentEmails() {
        try {
            List<EmailDTO> emails = gmailService.getRecentEmails(10);
            return ResponseEntity.ok(emails);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erreur: " + e.getMessage());
        }
    }
}
