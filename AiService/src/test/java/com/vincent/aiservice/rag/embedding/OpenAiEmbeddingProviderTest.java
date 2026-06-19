package com.vincent.aiservice.rag.embedding;

import com.vincent.aiservice.config.OpenAiProperties;
import com.vincent.aiservice.exception.ExternalAiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiEmbeddingProviderTest {

    @Test
    void embedPostsStructuredRequestAndReturnsFloatVectors() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiEmbeddingProvider provider = new OpenAiEmbeddingProvider(
                builder,
                new OpenAiProperties("key", "https://api.openai.test", "text-embedding-3-small", "gpt-test")
        );
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andExpect(header("Authorization", "Bearer key"))
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.input[0]").value("hello"))
                .andRespond(withSuccess("""
                        {"data":[{"embedding":[0.12,0.23]}]}
                        """, MediaType.APPLICATION_JSON));

        List<float[]> embeddings = provider.embed(List.of("hello"));

        assertThat(embeddings).hasSize(1);
        assertThat(embeddings.get(0)).containsExactly(0.12f, 0.23f);
        server.verify();
    }

    @Test
    void embedRejectsMissingApiKeyBeforeHttpCall() {
        OpenAiEmbeddingProvider provider = new OpenAiEmbeddingProvider(
                RestClient.builder(),
                new OpenAiProperties(" ", "https://api.openai.test", "text-embedding-3-small", "gpt-test")
        );

        assertThatThrownBy(() -> provider.embed("hello"))
                .isInstanceOf(ExternalAiException.class)
                .hasMessageContaining("API key not configured");
    }

    @Test
    void embedRejectsUnexpectedResponseSize() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiEmbeddingProvider provider = new OpenAiEmbeddingProvider(
                builder,
                new OpenAiProperties("key", "https://api.openai.test", "text-embedding-3-small", "gpt-test")
        );
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.embed("hello"))
                .isInstanceOf(ExternalAiException.class)
                .hasMessageContaining("Unexpected embeddings response size");
    }
}
