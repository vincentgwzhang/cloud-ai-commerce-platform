package com.vincent.aiservice.service;

import com.vincent.aiservice.dto.ChatRequest;
import com.vincent.aiservice.dto.ChatResponse;

public interface AiAssistantService {

    ChatResponse chat(String username, ChatRequest request);
}
