package com.vincent.aiservice.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDocumentFactoryTest {

    @Test
    void canonicalTextIncludesAvailableFields() {
        assertThat(ProductDocumentFactory.canonicalText("P-1", "Phone", "Great camera"))
                .isEqualTo("""
                        Product: Phone
                        Code: P-1
                        Description: Great camera""");
    }

    @Test
    void canonicalTextOmitsBlankOptionalFields() {
        assertThat(ProductDocumentFactory.canonicalText("P-2", " ", null))
                .isEqualTo("Code: P-2");
    }
}
