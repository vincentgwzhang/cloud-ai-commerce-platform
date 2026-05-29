package com.vincent.aiservice.rag.llm;

/**
 * Minimal chat-completion abstraction used by RAG answer generation.
 *
 * <p>Kept separate from the conversational {@code AiProvider} seam so the upcoming LLM Router
 * module can plug multiple providers behind this same interface.
 */
public interface LlmClient {

    /** Identifier of the chat model/backend, for metrics. */
    String model();

    /** Produce a completion for the given system + user prompt. */
    String complete(String systemPrompt, String userPrompt);
}
