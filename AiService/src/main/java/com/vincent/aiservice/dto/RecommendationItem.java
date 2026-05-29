package com.vincent.aiservice.dto;

public record RecommendationItem(
        String productCode,
        double score,
        String reason
) {
}
