package com.vincent.aiservice.rag;

import com.vincent.aiservice.rag.vector.ProductMatch;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Builds the grounded prompt: a system instruction plus the retrieved product context.
 */
@Component
public class RagPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a shopping assistant for an e-commerce platform.
            Answer the user's question using ONLY the product context provided.
            If the context is insufficient, say you don't have enough information.
            Be concise and cite product codes when relevant.""";

    public RagPrompt build(String question, List<ProductMatch> matches) {
        StringBuilder context = new StringBuilder();
        if (matches.isEmpty()) {
            context.append("(no matching products found)");
        } else {
            for (ProductMatch match : matches) {
                context.append("- [").append(match.productCode()).append("] ");
                context.append(StringUtils.hasText(match.document()) ? match.document() : "(no description)");
                context.append('\n');
            }
        }
        String user = "Product context:\n" + context + "\nQuestion: " + question;
        return new RagPrompt(SYSTEM_PROMPT, user);
    }

    public record RagPrompt(String system, String user) {
    }
}
