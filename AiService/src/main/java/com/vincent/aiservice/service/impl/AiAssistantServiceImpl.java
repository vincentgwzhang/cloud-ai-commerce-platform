package com.vincent.aiservice.service.impl;

import com.vincent.aiservice.ai.AiProvider;
import com.vincent.aiservice.dto.ChatRequest;
import com.vincent.aiservice.dto.ChatResponse;
import com.vincent.aiservice.memory.ConversationMemoryRepository;
import com.vincent.aiservice.memory.ConversationMessage;
import com.vincent.aiservice.observability.MdcKeys;
import com.vincent.aiservice.observability.MdcSupport;
import com.vincent.aiservice.service.AiAssistantService;
import com.vincent.aiservice.service.AiMetrics;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Orchestrates a single chat turn: persist the user message, generate an answer through the
 * pluggable {@link AiProvider}, persist the assistant reply, and surface tracing/metrics.
 *
 * <p>Memory persistence is best-effort — a Redis outage degrades to a stateless answer instead
 * of failing the request (DB/LLM remain the critical path).
 */
@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantServiceImpl.class);

    private final AiProvider aiProvider;
    private final ConversationMemoryRepository memoryRepository;
    private final AiMetrics aiMetrics;
    private final ObjectProvider<Tracer> tracerProvider;

    public AiAssistantServiceImpl(
            AiProvider aiProvider,
            ConversationMemoryRepository memoryRepository,
            AiMetrics aiMetrics,
            ObjectProvider<Tracer> tracerProvider
    ) {
        this.aiProvider = aiProvider;
        this.memoryRepository = memoryRepository;
        this.aiMetrics = aiMetrics;
        this.tracerProvider = tracerProvider;
    }

    @Override
    public ChatResponse chat(String username, ChatRequest request) {
        long startNanos = System.nanoTime();
        String conversationId = StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : UUID.randomUUID().toString();

        MdcSupport.put(MdcKeys.USERNAME, username);
        MDC.put("conversationId", conversationId);
        aiMetrics.recordChatRequest();
        try {
            log.info("AI_CHAT request username={} conversationId={} messageLength={}",
                    username, conversationId, request.message().length());

            persistMessageSafely(username, conversationId, ConversationMessage.user(request.message()));
            String answer = generateAnswer(username, conversationId, request.message());
            persistMessageSafely(username, conversationId, ConversationMessage.assistant(answer));

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            aiMetrics.recordChatLatency(durationMs);
            log.info("AI_CHAT response username={} conversationId={} provider={} durationMs={} answerLength={}",
                    username, conversationId, aiProvider.name(), durationMs, answer == null ? 0 : answer.length());

            return new ChatResponse(answer, conversationId, aiProvider.name(), MdcSupport.traceId().orElse(null));
        } catch (RuntimeException ex) {
            aiMetrics.recordChatFailure();
            log.error("AI_CHAT failed username={} conversationId={} provider={}",
                    username, conversationId, aiProvider.name(), ex);
            throw ex;
        } finally {
            MDC.remove("conversationId");
            MdcSupport.remove(MdcKeys.USERNAME);
        }
    }

    private String generateAnswer(String username, String conversationId, String message) {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return aiProvider.chat(username, message);
        }
        Span span = tracer.nextSpan().name("ai.chat.generate").start();
        span.tag("ai.provider", aiProvider.name());
        span.tag("ai.conversation_id", conversationId);
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return aiProvider.chat(username, message);
        } catch (RuntimeException ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    private void persistMessageSafely(String userId, String conversationId, ConversationMessage message) {
        try {
            memoryRepository.append(userId, conversationId, message);
        } catch (Exception ex) {
            log.warn("AI_CHAT memory persistence skipped conversationId={} role={} error={}",
                    conversationId, message.role(), ex.getMessage());
        }
    }
}
