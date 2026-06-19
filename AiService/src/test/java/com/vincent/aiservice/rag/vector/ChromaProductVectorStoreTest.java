package com.vincent.aiservice.rag.vector;

import com.vincent.aiservice.config.ChromaProperties;
import com.vincent.aiservice.config.RagProperties;
import com.vincent.aiservice.exception.ExternalAiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChromaProductVectorStoreTest {

    @Test
    void similaritySearchResolvesCollectionAndParsesMatches() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChromaProductVectorStore store = store(builder);
        server.expect(requestTo("http://chroma.test/api/v2/tenants/default/databases/default/collections"))
                .andExpect(jsonPath("$.name").value("products"))
                .andExpect(jsonPath("$.get_or_create").value(true))
                .andRespond(withSuccess("{\"id\":\"collection-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://chroma.test/api/v2/tenants/default/databases/default/collections/collection-1/query"))
                .andExpect(jsonPath("$.query_embeddings[0][0]").value(0.12))
                .andExpect(jsonPath("$.n_results").value(2))
                .andExpect(jsonPath("$.include[0]").value("documents"))
                .andExpect(jsonPath("$.include[1]").value("metadatas"))
                .andExpect(jsonPath("$.include[2]").value("distances"))
                .andRespond(withSuccess("""
                        {
                          "ids":[["P1","P2"]],
                          "documents":[["doc1","doc2"]],
                          "metadatas":[[{"productCode":"P1"},{"productCode":"P2"}]],
                          "distances":[[0.11,0.22]]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<ProductMatch> matches = store.similaritySearch(new float[]{0.12f, 0.23f}, 2);

        assertThat(matches).extracting(ProductMatch::productCode).containsExactly("P1", "P2");
        assertThat(matches).extracting(ProductMatch::distance).containsExactly(0.11, 0.22);
        server.verify();
    }

    @Test
    void upsertAndDeleteReuseCachedCollectionId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChromaProductVectorStore store = store(builder);
        server.expect(requestTo("http://chroma.test/api/v2/tenants/default/databases/default/collections"))
                .andRespond(withSuccess("{\"id\":\"collection-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://chroma.test/api/v2/tenants/default/databases/default/collections/collection-1/upsert"))
                .andExpect(jsonPath("$.ids[0]").value("P1"))
                .andExpect(jsonPath("$.embeddings[0][0]").value(0.12))
                .andExpect(jsonPath("$.documents[0]").value("doc1"))
                .andExpect(jsonPath("$.metadatas[0].name").value("Phone"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://chroma.test/api/v2/tenants/default/databases/default/collections/collection-1/delete"))
                .andExpect(jsonPath("$.ids[0]").value("P1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        store.upsert(new ProductVector("P1", new float[]{0.12f}, "doc1", Map.of("name", "Phone")));
        store.delete("P1");

        server.verify();
    }

    @Test
    void throwsWhenCollectionResponseHasNoId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChromaProductVectorStore store = store(builder);
        server.expect(requestTo("http://chroma.test/api/v2/tenants/default/databases/default/collections"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> store.similaritySearch(new float[]{0.1f}, 1))
                .isInstanceOf(ExternalAiException.class)
                .hasMessageContaining("collection id");
    }

    private static ChromaProductVectorStore store(RestClient.Builder builder) {
        return new ChromaProductVectorStore(
                builder,
                new ChromaProperties("http://chroma.test", "default", "default"),
                new RagProperties(3, "products", Duration.ofMinutes(5))
        );
    }
}
