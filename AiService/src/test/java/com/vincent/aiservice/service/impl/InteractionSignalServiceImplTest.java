package com.vincent.aiservice.service.impl;

import com.vincent.aiservice.entity.AiInteraction;
import com.vincent.aiservice.repository.AiInteractionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractionSignalServiceImplTest {

    private final AiInteractionRepository repository = mock(AiInteractionRepository.class);
    private final InteractionSignalServiceImpl service = new InteractionSignalServiceImpl(repository);

    @Test
    void recordInteractionPersistsSignal() {
        service.recordInteraction("u1", "P1", "ORDER", 5);

        verify(repository).save(argThat(signal ->
                "u1".equals(signal.getUsername())
                        && "P1".equals(signal.getProductCode())
                        && "ORDER".equals(signal.getInteractionType())
                        && signal.getWeight() == 5));
    }

    @Test
    void recentSignalProductCodesReturnsDistinctCodesInRepositoryOrder() {
        AiInteraction first = signal("P1");
        AiInteraction duplicate = signal("P1");
        AiInteraction second = signal("P2");
        when(repository.findTop200ByUsernameOrderByCreatedAtDesc("u1"))
                .thenReturn(List.of(first, duplicate, second));

        assertThat(service.recentSignalProductCodes("u1")).containsExactly("P1", "P2");
    }

    @Test
    void coOccurringProductsReturnsEmptyWithoutSeedsOrPeers() {
        assertThat(service.coOccurringProducts("u1", List.of(), 10)).isEmpty();
        assertThat(service.coOccurringProducts("u1", List.of("P1"), 0)).isEmpty();
        when(repository.findPeerUsernames("u1", List.of("P1"))).thenReturn(List.of());

        assertThat(service.coOccurringProducts("u1", List.of("P1"), 10)).isEmpty();
    }

    @Test
    void coOccurringProductsMapsAggregatedScores() {
        AiInteractionRepository.ProductScore score = score("P2", 9);
        when(repository.findPeerUsernames("u1", List.of("P1"))).thenReturn(List.of("u2"));
        when(repository.aggregatePeerProducts(eq(List.of("u2")), any(), any(Pageable.class)))
                .thenReturn(List.of(score));

        assertThat(service.coOccurringProducts("u1", List.of("P1"), 3))
                .extracting("productCode", "score")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("P2", 9.0));
    }

    @Test
    void popularProductsGuardsEmptyExcludeWithSentinel() {
        AiInteractionRepository.ProductScore score = score("P3", 7);
        when(repository.aggregatePopularProducts(any(), any(Pageable.class))).thenReturn(List.of(score));

        assertThat(service.popularProducts(null, 2))
                .extracting("productCode", "score")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("P3", 7.0));
        assertThat(service.popularProducts(List.of("P1"), 0)).isEmpty();
    }

    private static AiInteraction signal(String productCode) {
        AiInteraction signal = new AiInteraction();
        signal.setProductCode(productCode);
        return signal;
    }

    private static AiInteractionRepository.ProductScore score(String productCode, long value) {
        return new AiInteractionRepository.ProductScore() {
            @Override
            public String getProductCode() {
                return productCode;
            }

            @Override
            public long getScore() {
                return value;
            }
        };
    }
}
