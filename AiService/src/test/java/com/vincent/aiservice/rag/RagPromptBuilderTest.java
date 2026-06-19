package com.vincent.aiservice.rag;

import com.vincent.aiservice.rag.vector.ProductMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptBuilderTest {

    private final RagPromptBuilder promptBuilder = new RagPromptBuilder();

    @Test
    void buildIncludesRetrievedProductContextAndQuestion() {
        List<ProductMatch> matches = List.of(
                new ProductMatch("PHONE-001", 0.12, "Great camera phone with optical zoom.", Map.of()),
                new ProductMatch("PHONE-002", 0.24, "Budget phone with long battery life.", Map.of())
        );

        RagPromptBuilder.RagPrompt prompt = promptBuilder.build(
                "Which phone is best for photography?",
                matches
        );

        assertThat(prompt.system())
                .contains("You are a shopping assistant")
                .contains("using ONLY the product context provided");
        assertThat(prompt.user())
                .contains("Product context:")
                .contains("- [PHONE-001] Great camera phone with optical zoom.")
                .contains("- [PHONE-002] Budget phone with long battery life.")
                .contains("Question: Which phone is best for photography?");
    }

    @Test
    void buildUsesFallbackTextWhenNoProductsMatch() {
        RagPromptBuilder.RagPrompt prompt = promptBuilder.build(
                "Which laptop is best for gaming?",
                List.of()
        );

        assertThat(prompt.user())
                .contains("Product context:")
                .contains("(no matching products found)")
                .contains("Question: Which laptop is best for gaming?");
    }

    @Test
    void buildUsesFallbackTextWhenProductDocumentIsBlank() {
        List<ProductMatch> matches = List.of(
                new ProductMatch("PHONE-003", 0.31, "   ", Map.of())
        );

        RagPromptBuilder.RagPrompt prompt = promptBuilder.build(
                "Tell me about this phone.",
                matches
        );

        assertThat(prompt.user())
                .contains("- [PHONE-003] (no description)")
                .contains("Question: Tell me about this phone.");
    }
}
