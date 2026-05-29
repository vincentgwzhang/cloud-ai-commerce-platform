package com.vincent.aiservice.dto;

import java.time.Instant;
import java.util.List;

public record RecommendationResponse(
        String username,
        String source,
        List<RecommendationItem> items,
        Instant generatedAt
) {
}
