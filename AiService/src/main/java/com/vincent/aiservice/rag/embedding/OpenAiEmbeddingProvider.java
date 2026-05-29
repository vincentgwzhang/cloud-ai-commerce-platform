package com.vincent.aiservice.rag.embedding;

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
 * OpenAI embeddings backend ({@code POST /v1/embeddings}).
 *
 * <p>Requests/responses are modelled as plain maps to stay resilient to minor API shape changes
 * and to avoid coupling to a specific JSON-binding annotation set.
 */
@Component
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public OpenAiEmbeddingProvider(RestClient.Builder builder, OpenAiProperties properties) {
        this.properties = properties;
        this.restClient = builder.clone().baseUrl(properties.baseUrl()).build();
    }

    @Override
    public String model() {
        return properties.embeddingModel();
    }

    @Override
    public float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        requireApiKey();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.embeddingModel());
        body.put("input", texts);

        Map<String, Object> response = post(body);
        List<?> data = asList(response.get("data"));
        if (data == null || data.size() != texts.size()) {
            throw new ExternalAiException("Unexpected embeddings response size from OpenAI");
        }
        return data.stream().map(OpenAiEmbeddingProvider::toVector).toList();
    }

    private Map<String, Object> post(Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri("/v1/embeddings")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientException ex) {
            throw new ExternalAiException("OpenAI embeddings request failed: " + ex.getMessage(), ex);
        }
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new ExternalAiException("OpenAI API key not configured (set OPENAI_API_KEY)");
        }
    }

    private static float[] toVector(Object item) {
        Map<?, ?> map = (Map<?, ?>) item;
        List<?> embedding = asList(map.get("embedding"));
        if (embedding == null) {
            throw new ExternalAiException("Missing embedding in OpenAI response");
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = ((Number) embedding.get(i)).floatValue();
        }
        return vector;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : null;
    }
}
