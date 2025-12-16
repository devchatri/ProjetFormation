package com.example.mini_mindy_backend.service;

public interface AirflowService {

    void triggerPipelineAsync(String email, String refreshToken);

    void triggerPipeline(String email, String refreshToken);
}
