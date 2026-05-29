package com.vincent.aiservice.service;

import com.vincent.aiservice.dto.RecommendationResponse;

public interface RecommendationService {

    RecommendationResponse recommend(String username, String context);

    RecommendationResponse recommendForProduct(String username, String productCode);

    void evictRecommendations(String username);
}
