package com.vincent.aiservice.controller;

import com.vincent.aiservice.dto.AskRequest;
import com.vincent.aiservice.dto.AskResponse;
import com.vincent.aiservice.dto.SourceRef;
import com.vincent.aiservice.rag.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagControllerTest {

    private final RagService ragService = mock(RagService.class);
    private final RagController controller = new RagController(ragService);

    @Test
    void askDelegatesWithJwtSubject() {
        AskResponse response = new AskResponse("answer", List.of(new SourceRef("P1", 0.12)), "gpt-test");
        when(ragService.ask("u1", "Which phone?")).thenReturn(response);

        assertThat(controller.ask(jwt(), new AskRequest("Which phone?")).data()).isSameAs(response);
        verify(ragService).ask("u1", "Which phone?");
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token").header("alg", "none").subject("u1").build();
    }
}
