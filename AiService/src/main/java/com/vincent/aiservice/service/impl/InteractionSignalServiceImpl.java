package com.vincent.aiservice.service.impl;

import com.vincent.aiservice.entity.AiInteraction;
import com.vincent.aiservice.repository.AiInteractionRepository;
import com.vincent.aiservice.service.InteractionSignalService;
import com.vincent.aiservice.service.ScoredProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * MySQL-backed behaviour-signal store and candidate generator.
 *
 * <p>Empty {@code IN (...)} collections are guarded with a sentinel so the JPQL queries behave
 * consistently across dialects regardless of whether the user has any history yet.
 */
@Service
public class InteractionSignalServiceImpl implements InteractionSignalService {

    private static final Logger log = LoggerFactory.getLogger(InteractionSignalServiceImpl.class);

    /** Placeholder that can never match a real product code, keeping {@code NOT IN} non-empty. */
    private static final String NO_MATCH = "\u0000__none__";

    private final AiInteractionRepository repository;

    public InteractionSignalServiceImpl(AiInteractionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void recordInteraction(String username, String productCode, String interactionType, int weight) {
        AiInteraction signal = new AiInteraction();
        signal.setUsername(username);
        signal.setProductCode(productCode);
        signal.setInteractionType(interactionType);
        signal.setWeight(weight);
        repository.save(signal);
        log.info("AI_SIGNAL recorded username={} productCode={} type={} weight={}",
                username, productCode, interactionType, weight);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> recentSignalProductCodes(String username) {
        return repository.findTop200ByUsernameOrderByCreatedAtDesc(username).stream()
                .map(AiInteraction::getProductCode)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoredProduct> coOccurringProducts(String username, List<String> seeds, int maxItems) {
        if (seeds == null || seeds.isEmpty() || maxItems <= 0) {
            return List.of();
        }
        List<String> peers = repository.findPeerUsernames(username, seeds);
        if (peers.isEmpty()) {
            return List.of();
        }
        return repository.aggregatePeerProducts(peers, withSentinel(seeds), PageRequest.of(0, maxItems)).stream()
                .map(row -> new ScoredProduct(row.getProductCode(), row.getScore()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoredProduct> popularProducts(Collection<String> excludeProductCodes, int maxItems) {
        if (maxItems <= 0) {
            return List.of();
        }
        return repository.aggregatePopularProducts(withSentinel(excludeProductCodes), PageRequest.of(0, maxItems)).stream()
                .map(row -> new ScoredProduct(row.getProductCode(), row.getScore()))
                .toList();
    }

    private static Collection<String> withSentinel(Collection<String> codes) {
        List<String> guarded = new ArrayList<>();
        if (codes != null) {
            guarded.addAll(codes);
        }
        guarded.add(NO_MATCH);
        return guarded;
    }
}
