package com.example.mini_mindy_backend.controller;



import com.example.mini_mindy_backend.service.EmailEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailEmbeddingController {

    private final EmailEmbeddingService emailService;

    @PostMapping("/process/{userId}")
    public String processEmails(@PathVariable String userId) throws Exception {
        emailService.processEmailsFromMinIO(userId); 
        return "Processing started for user: " + userId;
    }

}