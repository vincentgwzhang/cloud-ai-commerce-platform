package com.vincent.aiservice.repository;

import com.vincent.aiservice.entity.AiInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, Long> {

    List<AiInteraction> findByUsername(String username);
}
