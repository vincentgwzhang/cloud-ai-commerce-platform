package com.vincent.aiservice.rag.llm;

import com.vincent.aiservice.config.OpenAiProperties;
import com.vincent.aiservice.exception.ExternalAiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiLlmClientTest {

    @Test
    void completePostsChatMessagesAndReturnsFirstChoiceContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiLlmClient client = new OpenAiLlmClient(
                builder,
                new OpenAiProperties("key", "https://api.openai.test", "text-embedding-3-small", "gpt-4o-mini")
        );
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer key"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.temperature").value(0.2))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("system prompt"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("user prompt"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"answer"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.complete("system prompt", "user prompt")).isEqualTo("answer");
        server.verify();
    }

    @Test
    void completeRejectsMissingApiKey() {
        OpenAiLlmClient client = new OpenAiLlmClient(
                RestClient.builder(),
                new OpenAiProperties("", "https://api.openai.test", "text-embedding-3-small", "gpt-4o-mini")
        );

        assertThatThrownBy(() -> client.complete("system", "user"))
                .isInstanceOf(ExternalAiException.class)
                .hasMessageContaining("API key not configured");
    }

    @Test
    void completeRejectsEmptyChoices() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiLlmClient client = new OpenAiLlmClient(
                builder,
                new OpenAiProperties("key", "https://api.openai.test", "text-embedding-3-small", "gpt-4o-mini")
        );
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("system", "user"))
                .isInstanceOf(ExternalAiException.class)
                .hasMessageContaining("no choices");
    }
}
