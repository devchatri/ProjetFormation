package com.example.mini_mindy_backend.service.impl;



import com.example.mini_mindy_backend.dto.EmailDTO;
import com.example.mini_mindy_backend.model.User;
import com.example.mini_mindy_backend.repository.UserRepository;
import com.example.mini_mindy_backend.service.GmailService;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GmailServiceImpl implements GmailService {

    private static final String APPLICATION_NAME = "Mini Mindy";
    private static final String USER_ID = "me";
    @Autowired
    private UserRepository userRepository;

    private Gmail getGmailServiceForUser(String email) throws Exception {
        GoogleClientSecrets secrets = GoogleClientSecrets.load(
                JacksonFactory.getDefaultInstance(),
                new FileReader("src/main/resources/credentials.json")
        );

        TokenResponse token = new TokenResponse();
        User user = userRepository.findByEmail(email).orElseThrow();
        String refreshToken = user.getGoogleRefreshToken();
        token.setRefreshToken(refreshToken);

        Credential credential = new Credential
                .Builder(BearerToken.authorizationHeaderAccessMethod())
                .setJsonFactory(JacksonFactory.getDefaultInstance())
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setClientAuthentication(new BasicAuthentication(
                        secrets.getDetails().getClientId(),
                        secrets.getDetails().getClientSecret()
                ))
                .setTokenServerEncodedUrl("https://oauth2.googleapis.com/token")
                .build();

        credential.setRefreshToken(refreshToken);

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
        ).setApplicationName(APPLICATION_NAME).build();
    }

    @Override
    public List<EmailDTO> getRecentEmailsForUser(String refreshToken, int maxResults) throws Exception {
        Gmail service = getGmailServiceForUser(refreshToken);
        ListMessagesResponse response = service.users().messages()
                .list(USER_ID)
                .setMaxResults((long) maxResults)
                .execute();

        List<EmailDTO> emails = new ArrayList<>();

        if (response.getMessages() != null) {
            for (Message msg : response.getMessages()) {
                Message full = service.users().messages().get(USER_ID, msg.getId()).execute();

                EmailDTO dto = new EmailDTO();
                dto.setId(full.getId());
                dto.setSnippet(full.getSnippet());

                for (MessagePartHeader h : full.getPayload().getHeaders()) {
                    switch (h.getName()) {
                        case "Subject": dto.setSubject(h.getValue()); break;
                        case "From": dto.setFrom(h.getValue()); break;
                        case "Date": dto.setDate(h.getValue()); break;
                    }
                }

                dto.setBody(extractBody(full.getPayload()));
                emails.add(dto);
            }
        }
        return emails;
    }

    private String extractBody(MessagePart part) {
        try {
            if (part.getBody() != null && part.getBody().getData() != null) {
                return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            }

            if (part.getParts() != null) {
                for (MessagePart sub : part.getParts()) {
                    String res = extractBody(sub);
                    if (!res.isEmpty()) return res;
                }
            }
        } catch (Exception ignored) {}

        return "";
    }
}
