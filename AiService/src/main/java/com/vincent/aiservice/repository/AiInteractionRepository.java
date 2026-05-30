package com.vincent.aiservice.repository;

import com.vincent.aiservice.entity.AiInteraction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, Long> {

    List<AiInteraction> findByUsername(String username);

    /** Most recent signals for a user (bounded), newest first — used to derive recommendation seeds. */
    List<AiInteraction> findTop200ByUsernameOrderByCreatedAtDesc(String username);

    /**
     * Peers = other users who interacted with any of the user's seed products. The basis of
     * user-based collaborative filtering.
     */
    @Query("""
            select distinct i.username
            from AiInteraction i
            where i.productCode in :seeds
              and i.username <> :username
            """)
    List<String> findPeerUsernames(@Param("username") String username,
                                   @Param("seeds") Collection<String> seeds);

    /**
     * Products favoured by the given peers, excluding what the user already has, ranked by
     * summed interaction weight (DB-side aggregation).
     */
    @Query("""
            select i.productCode as productCode, sum(i.weight) as score
            from AiInteraction i
            where i.username in :peers
              and i.productCode not in :exclude
            group by i.productCode
            order by sum(i.weight) desc
            """)
    List<ProductScore> aggregatePeerProducts(@Param("peers") Collection<String> peers,
                                             @Param("exclude") Collection<String> exclude,
                                             Pageable pageable);

    /** Global popularity ranking by summed interaction weight (cold-start fallback). */
    @Query("""
            select i.productCode as productCode, sum(i.weight) as score
            from AiInteraction i
            where i.productCode not in :exclude
            group by i.productCode
            order by sum(i.weight) desc
            """)
    List<ProductScore> aggregatePopularProducts(@Param("exclude") Collection<String> exclude,
                                                Pageable pageable);

    /** Projection for the grouped aggregation queries above. */
    interface ProductScore {
        String getProductCode();

        long getScore();
    }
}
