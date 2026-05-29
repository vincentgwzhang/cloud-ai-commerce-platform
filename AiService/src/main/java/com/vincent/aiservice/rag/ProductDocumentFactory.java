package com.vincent.aiservice.rag;

import org.springframework.util.StringUtils;

/** Builds the canonical text that represents a product for embedding/retrieval. */
public final class ProductDocumentFactory {

    private ProductDocumentFactory() {
    }

    public static String canonicalText(String productCode, String name, String description) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(name)) {
            sb.append("Product: ").append(name).append('\n');
        }
        sb.append("Code: ").append(productCode).append('\n');
        if (StringUtils.hasText(description)) {
            sb.append("Description: ").append(description);
        }
        return sb.toString().strip();
    }
}
