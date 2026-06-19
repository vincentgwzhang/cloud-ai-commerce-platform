package com.vincent.aiservice.rag.llm;

import com.vincent.aiservice.config.OpenAiProperties;
import com.vincent.aiservice.exception.ExternalAiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * OpenAI chat-completions backend ({@code POST /v1/chat/completions}).
 */
@Component
public class OpenAiLlmClient implements LlmClient {

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public OpenAiLlmClient(RestClient.Builder builder, OpenAiProperties properties) {
        this.properties = properties;
        this.restClient = builder.clone().baseUrl(properties.baseUrl()).build();
    }

    @Override
    public String model() {
        return properties.chatModel();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        requireApiKey();
        OpenAiChatRequest body = new OpenAiChatRequest(
                properties.chatModel(),
                0.2,
                List.of(
                        new OpenAiChatMessage("system", systemPrompt),
                        new OpenAiChatMessage("user", userPrompt)
                )
        );

        OpenAiChatResponse response = post(body);
        return extractContent(response);
    }

    private OpenAiChatResponse post(OpenAiChatRequest body) {
        try {
            return restClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(OpenAiChatResponse.class);
        } catch (RestClientException ex) {
            throw new ExternalAiException("OpenAI chat request failed: " + ex.getMessage(), ex);
        }
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new ExternalAiException("OpenAI API key not configured (set OPENAI_API_KEY)");
        }
    }

    private static String extractContent(OpenAiChatResponse response) {
        List<OpenAiChatChoice> choices = response == null ? null : response.choices();
        if (choices == null || choices.isEmpty()) {
            throw new ExternalAiException("OpenAI chat response had no choices");
        }
        OpenAiChatMessage message = choices.get(0).message();
        if (message == null || !StringUtils.hasText(message.content())) {
            throw new ExternalAiException("OpenAI chat response missing message content");
        }
        return message.content();
    }

    private record OpenAiChatRequest(
            String model,
            double temperature,
            List<OpenAiChatMessage> messages
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiChatResponse(
            List<OpenAiChatChoice> choices
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiChatChoice(
            OpenAiChatMessage message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiChatMessage(
            String role,
            String content
    ) {
    }
}
