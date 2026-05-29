package com.vincent.aiservice.rag.llm;

import com.vincent.aiservice.config.OpenAiProperties;
import com.vincent.aiservice.exception.ExternalAiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI chat-completions backend ({@code POST /v1/chat/completions}).
 */
@Component
public class OpenAiLlmClient implements LlmClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.chatModel());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        Map<String, Object> response = post(body);
        return extractContent(response);
    }

    private Map<String, Object> post(Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientException ex) {
            throw new ExternalAiException("OpenAI chat request failed: " + ex.getMessage(), ex);
        }
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new ExternalAiException("OpenAI API key not configured (set OPENAI_API_KEY)");
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractContent(Map<String, Object> response) {
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            throw new ExternalAiException("OpenAI chat response had no choices");
        }
        Map<String, Object> first = (Map<String, Object>) list.get(0);
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        if (message == null || !(message.get("content") instanceof String content)) {
            throw new ExternalAiException("OpenAI chat response missing message content");
        }
        return content;
    }
}
