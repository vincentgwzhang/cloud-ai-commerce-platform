package com.vincent.aiservice.repository;

import com.vincent.aiservice.entity.AiRecommendationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecommendationLogRepository extends JpaRepository<AiRecommendationLog, Long> {
}
