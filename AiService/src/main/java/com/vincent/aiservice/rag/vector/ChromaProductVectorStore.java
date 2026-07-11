package com.vincent.aiservice.rag.vector;

import com.vincent.aiservice.config.ChromaProperties;
import com.vincent.aiservice.config.RagProperties;
import com.vincent.aiservice.exception.ExternalAiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * ChromaDB-backed {@link ProductVectorStore} (Chroma v2 REST API: tenant/database scoped).
 *
 * <p>The collection id is resolved lazily via {@code get_or_create} and cached. Embeddings are
 * sent as plain number lists.
 *
 * <p>Swapping to another vector database only requires a new {@link ProductVectorStore}
 * implementation — nothing else in the RAG pipeline changes.
 */
@Component
public class ChromaProductVectorStore implements ProductVectorStore {

    private final RestClient restClient;
    private final RagProperties ragProperties;
    private final String collectionsBasePath;

    private volatile String collectionId;

    public ChromaProductVectorStore(RestClient.Builder builder, ChromaProperties chromaProperties, RagProperties ragProperties) {
        this.ragProperties = ragProperties;
        this.restClient = builder.clone().baseUrl(chromaProperties.baseUrl()).build();
        this.collectionsBasePath = "/api/v2/tenants/" + chromaProperties.tenant()
                + "/databases/" + chromaProperties.database() + "/collections";
    }

    @Override
    public void upsert(ProductVector vector) {
        String id = collectionId();
        postMutation(
                collectionsBasePath + "/" + id + "/upsert",
                new ChromaUpsertRequest(
                        List.of(vector.productCode()),
                        List.of(toList(vector.embedding())),
                        List.of(vector.document()),
                        List.of(metadata(vector))
                )
        );
    }

    @Override
    public void delete(String productCode) {
        String id = collectionId();
        postMutation(collectionsBasePath + "/" + id + "/delete", new ChromaDeleteRequest(List.of(productCode)));
    }

    @Override
    public List<ProductMatch> similaritySearch(float[] queryEmbedding, int topK) {
        String id = collectionId();
        ChromaQueryResponse response = postQuery(
                collectionsBasePath + "/" + id + "/query",
                new ChromaQueryRequest(
                        List.of(toList(queryEmbedding)),
                        topK,
                        List.of("documents", "metadatas", "distances")
                )
        );
        return parseMatches(response);
    }

    private String collectionId() {
        String local = collectionId;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (collectionId == null) {
                ChromaCollectionResponse response = postCollection(
                        collectionsBasePath,
                        new ChromaCollectionRequest(ragProperties.collection(), true)
                );
                if (response == null || response.id() == null) {
                    throw new ExternalAiException("Chroma did not return a collection id");
                }
                collectionId = response.id();
            }
            return collectionId;
        }
    }

    private ChromaCollectionResponse postCollection(String uri, ChromaCollectionRequest body) {
        try {
            return restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(ChromaCollectionResponse.class);
        } catch (RestClientException ex) {
            throw new ExternalAiException("Chroma request failed (" + uri + "): " + ex.getMessage(), ex);
        }
    }

    private ChromaQueryResponse postQuery(String uri, ChromaQueryRequest body) {
        try {
            return restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(ChromaQueryResponse.class);
        } catch (RestClientException ex) {
            throw new ExternalAiException("Chroma request failed (" + uri + "): " + ex.getMessage(), ex);
        }
    }

    private ChromaMutationResponse postMutation(String uri, Object body) {
        try {
            return restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(ChromaMutationResponse.class);
        } catch (RestClientException ex) {
            throw new ExternalAiException("Chroma request failed (" + uri + "): " + ex.getMessage(), ex);
        }
    }

    private static List<ProductMatch> parseMatches(ChromaQueryResponse response) {
        if (response == null) {
            return List.of();
        }
        List<String> ids = firstRow(response.ids());
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> documents = firstRow(response.documents());
        List<Map<String, Object>> metadatas = firstRow(response.metadatas());
        List<Double> distances = firstRow(response.distances());

        List<ProductMatch> matches = new java.util.ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String document = i < documents.size() ? documents.get(i) : null;
            double distance = i < distances.size() ? distances.get(i) : Double.NaN;
            Map<String, Object> metadata = i < metadatas.size() ? metadatas.get(i) : Map.of();
            matches.add(new ProductMatch(ids.get(i), distance, document, metadata));
        }
        return matches;
    }

    private static <T> List<T> firstRow(List<List<T>> value) {
        return value == null || value.isEmpty() || value.get(0) == null ? List.of() : value.get(0);
    }

    private static List<Float> toList(float[] embedding) {
        List<Float> list = new java.util.ArrayList<>(embedding.length);
        for (float v : embedding) {
            list.add(v);
        }
        return list;
    }

    private static ProductVectorMetadata metadata(ProductVector vector) {
        return vector.metadata() == null
                ? new ProductVectorMetadata(vector.productCode(), null, null, null)
                : vector.metadata();
    }

    private record ChromaCollectionRequest(
            String name,
            boolean get_or_create
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChromaCollectionResponse(
            String id
    ) {
    }

    private record ChromaQueryRequest(
            List<List<Float>> query_embeddings,
            int n_results,
            List<String> include
    ) {
    }

    private record ChromaUpsertRequest(
            List<String> ids,
            List<List<Float>> embeddings,
            List<String> documents,
            List<ProductVectorMetadata> metadatas
    ) {
    }

    private record ChromaDeleteRequest(
            List<String> ids
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChromaMutationResponse() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChromaQueryResponse(
            List<List<String>> ids,
            List<List<String>> documents,
            List<List<Map<String, Object>>> metadatas,
            List<List<Double>> distances
    ) {
    }
}
