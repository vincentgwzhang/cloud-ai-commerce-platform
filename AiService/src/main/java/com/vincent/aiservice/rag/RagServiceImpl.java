package com.vincent.aiservice.rag;

import com.vincent.aiservice.config.RagProperties;
import com.vincent.aiservice.dto.AskResponse;
import com.vincent.aiservice.dto.SourceRef;
import com.vincent.aiservice.rag.embedding.EmbeddingProvider;
import com.vincent.aiservice.rag.llm.LlmClient;
import com.vincent.aiservice.rag.vector.ProductMatch;
import com.vincent.aiservice.rag.vector.ProductVectorStore;
import com.vincent.aiservice.service.AiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG pipeline: question → embedding → similarity search → prompt construction → LLM → answer.
 *
 * <p>Every collaborator is an abstraction ({@link EmbeddingProvider}, {@link ProductVectorStore},
 * {@link LlmClient}), so backends are swappable without changing this orchestration.
 */
@Service
public class RagServiceImpl implements RagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    private final EmbeddingProvider embeddingProvider;
    private final ProductVectorStore vectorStore;
    private final LlmClient llmClient;
    private final RagPromptBuilder promptBuilder;
    private final RagProperties ragProperties;
    private final AiMetrics aiMetrics;

    public RagServiceImpl(
            EmbeddingProvider embeddingProvider,
            ProductVectorStore vectorStore,
            LlmClient llmClient,
            RagPromptBuilder promptBuilder,
            RagProperties ragProperties,
            AiMetrics aiMetrics
    ) {
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.ragProperties = ragProperties;
        this.aiMetrics = aiMetrics;
    }

    @Override
    public AskResponse ask(String username, String question) {
        long startNanos = System.nanoTime();
        aiMetrics.recordRagAskRequest();
        try {
            float[] queryEmbedding = embeddingProvider.embed(question);
            List<ProductMatch> matches = vectorStore.similaritySearch(queryEmbedding, ragProperties.topK());
            RagPromptBuilder.RagPrompt prompt = promptBuilder.build(question, matches);
            String answer = llmClient.complete(prompt.system(), prompt.user());

            List<SourceRef> sources = matches.stream()
                    .map(match -> new SourceRef(match.productCode(), match.distance()))
                    .toList();

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            aiMetrics.recordRagAskLatency(durationMs);
            log.info("RAG_ASK username={} matches={} model={} durationMs={}",
                    username, matches.size(), llmClient.model(), durationMs);
            return new AskResponse(answer, sources, llmClient.model());
        } catch (RuntimeException ex) {
            aiMetrics.recordRagAskFailure();
            log.error("RAG_ASK failed username={}", username, ex);
            throw ex;
        }
    }
}
