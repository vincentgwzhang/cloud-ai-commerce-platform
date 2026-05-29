package com.vincent.aiservice.rag;

import com.vincent.aiservice.dto.AskResponse;

public interface RagService {

    /** Answer a natural-language question grounded in the product knowledge base. */
    AskResponse ask(String username, String question);
}
