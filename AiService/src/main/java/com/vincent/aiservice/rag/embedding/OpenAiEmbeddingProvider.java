package com.vincent.aiservice.rag.embedding;

import com.vincent.aiservice.config.OpenAiProperties;
import com.vincent.aiservice.exception.ExternalAiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * OpenAI embeddings backend ({@code POST /v1/embeddings}).
 *
 * <p>Requests/responses are modelled as plain maps to stay resilient to minor API shape changes
 * and to avoid coupling to a specific JSON-binding annotation set.
 */
@Component
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

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
        OpenAiEmbeddingResponse response = post(new OpenAiEmbeddingRequest(properties.embeddingModel(), texts));
        List<EmbeddingData> data = response == null ? null : response.data();
        if (data == null || data.size() != texts.size()) {
            throw new ExternalAiException("Unexpected embeddings response size from OpenAI");
        }
        return data.stream()
                .map(EmbeddingData::embedding)
                .map(OpenAiEmbeddingProvider::toVector)
                .toList();
    }

    private OpenAiEmbeddingResponse post(OpenAiEmbeddingRequest body) {
        try {
            return restClient.post()
                    .uri("/v1/embeddings")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class);
        } catch (RestClientException ex) {
            throw new ExternalAiException("OpenAI embeddings request failed: " + ex.getMessage(), ex);
        }
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new ExternalAiException("OpenAI API key not configured (set OPENAI_API_KEY)");
        }
    }

    private static float[] toVector(List<Float> embedding) {
        if (embedding == null) {
            throw new ExternalAiException("Missing embedding in OpenAI response");
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i);
        }
        return vector;
    }

    private record OpenAiEmbeddingRequest(
            String model,
            List<String> input
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiEmbeddingResponse(
            List<EmbeddingData> data
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(
            List<Float> embedding
    ) {
    }
}
