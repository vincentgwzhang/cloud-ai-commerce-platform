package com.vincent.aiservice.service.impl;

import com.vincent.aiservice.repository.AiInteractionRepository;
import com.vincent.aiservice.service.InteractionSignalService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InteractionSignalServiceImpl implements InteractionSignalService {

    private final AiInteractionRepository aiInteractionRepository;

    public InteractionSignalServiceImpl(AiInteractionRepository aiInteractionRepository) {
        this.aiInteractionRepository = aiInteractionRepository;
    }

    @Override
    public void recordInteraction(String username, String productCode, String interactionType, int weight) {
        // TODO: persist a new AiInteraction row for this behaviour signal
    }

    @Override
    public List<String> recentSignalProductCodes(String username) {
        // TODO: load recent interactions for username and project to product codes
        return List.of();
    }
}
