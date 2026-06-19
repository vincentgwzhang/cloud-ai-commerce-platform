package com.vincent.aiservice.controller;

import com.vincent.aiservice.dto.ConversationResponse;
import com.vincent.aiservice.memory.ConversationMessage;
import com.vincent.aiservice.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationControllerTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConversationController controller = new ConversationController(conversationService);

    @Test
    void getDelegatesWithJwtSubject() {
        ConversationResponse response = new ConversationResponse("c1", List.of(ConversationMessage.user("hello")));
        when(conversationService.getConversation("u1", "c1")).thenReturn(response);

        assertThat(controller.get(jwt(), "c1").data()).isSameAs(response);
    }

    @Test
    void deleteDelegatesAndReturnsDeletedMessage() {
        var response = controller.delete(jwt(), "c1");

        assertThat(response.message()).isEqualTo("deleted");
        verify(conversationService).deleteConversation("u1", "c1");
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token").header("alg", "none").subject("u1").build();
    }
}
