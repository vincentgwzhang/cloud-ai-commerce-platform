package com.vincent.aiservice.service;

import java.util.List;

public interface InteractionSignalService {

    void recordInteraction(String username, String productCode, String interactionType, int weight);

    List<String> recentSignalProductCodes(String username);
}
