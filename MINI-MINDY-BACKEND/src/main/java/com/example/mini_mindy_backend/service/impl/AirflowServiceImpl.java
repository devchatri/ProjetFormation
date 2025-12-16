package com.example.mini_mindy_backend.service.impl;

import com.example.mini_mindy_backend.service.AirflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AirflowServiceImpl implements AirflowService {

    private final RestTemplate restTemplate;

    @Value("${airflow.api.url:http://localhost:8080}")
    private String airflowApiUrl;

    public AirflowServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void triggerPipelineAsync(String email, String refreshToken) {
        new Thread(() -> {
            try {
                triggerPipeline(email, refreshToken);
            } catch (Exception e) {
                log.error("[AIRFLOW] Error triggering pipeline asynchronously for email {}: {}", email, e.getMessage());
            }
        }).start();
    }

    @Override
    public void triggerPipeline(String email, String refreshToken) {
        try {
            String dagId = "email_intelligence_pipeline";
            String url = airflowApiUrl + "/api/v1/dags/" + dagId + "/dagRuns";

            // Build configuration parameters for Airflow
            Map<String, Object> conf = new HashMap<>();
            conf.put("email", email);
            conf.put("refrechtoken", refreshToken); // Note: "refrechtoken" as in the existing DAG

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("conf", conf);

            // Create headers with Basic Auth authentication (admin:admin)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            String auth = "admin:admin";
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);

            // Create HTTP entity with headers
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.info("[AIRFLOW] Triggering DAG: {} for email: {}", dagId, email);

            restTemplate.postForEntity(url, requestEntity, String.class);

            log.info("[AIRFLOW] Successfully triggered DAG for email: {}", email);

        } catch (RestClientException e) {
            log.error("[AIRFLOW] Error triggering Airflow DAG: {}", e.getMessage());
            throw new RuntimeException("Failed to trigger Airflow pipeline", e);
        }
    }
}
