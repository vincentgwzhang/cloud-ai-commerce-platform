package com.vincent.aiservice.rag.vector;

import com.vincent.aiservice.config.ChromaProperties;
import com.vincent.aiservice.config.RagProperties;
import com.vincent.aiservice.exception.ExternalAiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ChromaDB-backed {@link ProductVectorStore} (Chroma v2 REST API: tenant/database scoped).
 *
 * <p>The collection id is resolved lazily via {@code get_or_create} and cached. Embeddings are
 * sent as plain number lists; responses are parsed from maps to avoid binding-annotation coupling.
 *
 * <p>Swapping to another vector database only requires a new {@link ProductVectorStore}
 * implementation — nothing else in the RAG pipeline changes.
 */
@Component
public class ChromaProductVectorStore implements ProductVectorStore {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(vector.productCode()));
        body.put("embeddings", List.of(toList(vector.embedding())));
        body.put("documents", List.of(vector.document()));
        body.put("metadatas", List.of(vector.metadata() == null ? Map.of() : vector.metadata()));
        post(collectionsBasePath + "/" + id + "/upsert", body);
    }

    @Override
    public void delete(String productCode) {
        String id = collectionId();
        post(collectionsBasePath + "/" + id + "/delete", Map.of("ids", List.of(productCode)));
    }

    @Override
    public List<ProductMatch> similaritySearch(float[] queryEmbedding, int topK) {
        String id = collectionId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query_embeddings", List.of(toList(queryEmbedding)));
        body.put("n_results", topK);
        body.put("include", List.of("documents", "metadatas", "distances"));
        Map<String, Object> response = post(collectionsBasePath + "/" + id + "/query", body);
        return parseMatches(response);
    }

    private String collectionId() {
        String local = collectionId;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (collectionId == null) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("name", ragProperties.collection());
                body.put("get_or_create", true);
                Map<String, Object> response = post(collectionsBasePath, body);
                Object id = response.get("id");
                if (!(id instanceof String idString)) {
                    throw new ExternalAiException("Chroma did not return a collection id");
                }
                collectionId = idString;
            }
            return collectionId;
        }
    }

    private Map<String, Object> post(String uri, Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientException ex) {
            throw new ExternalAiException("Chroma request failed (" + uri + "): " + ex.getMessage(), ex);
        }
    }

    private static List<ProductMatch> parseMatches(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        List<String> ids = firstRow(response.get("ids"));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> documents = firstRow(response.get("documents"));
        List<?> metadatas = firstRowRaw(response.get("metadatas"));
        List<?> distances = firstRowRaw(response.get("distances"));

        List<ProductMatch> matches = new java.util.ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String document = i < documents.size() ? documents.get(i) : null;
            double distance = distances != null && i < distances.size()
                    ? ((Number) distances.get(i)).doubleValue() : Double.NaN;
            Map<String, Object> metadata = metadatas != null && i < metadatas.size()
                    ? castMetadata(metadatas.get(i)) : Map.of();
            matches.add(new ProductMatch(ids.get(i), distance, document, metadata));
        }
        return matches;
    }

    @SuppressWarnings("unchecked")
    private static List<String> firstRow(Object value) {
        List<?> raw = firstRowRaw(value);
        return raw == null ? List.of() : (List<String>) raw;
    }

    private static List<?> firstRowRaw(Object value) {
        if (value instanceof List<?> outer && !outer.isEmpty() && outer.get(0) instanceof List<?> inner) {
            return inner;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMetadata(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<Float> toList(float[] embedding) {
        List<Float> list = new java.util.ArrayList<>(embedding.length);
        for (float v : embedding) {
            list.add(v);
        }
        return list;
    }
}
