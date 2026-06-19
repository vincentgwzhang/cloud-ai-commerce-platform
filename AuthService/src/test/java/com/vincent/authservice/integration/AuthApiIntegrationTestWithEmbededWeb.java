package com.vincent.authservice.integration;

import com.jayway.jsonpath.JsonPath;
import com.vincent.authservice.repository.RefreshTokenRepository;
import com.vincent.authservice.repository.UserRepository;
import com.vincent.authservice.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 这个类演示另一种集成测试方式：
 *
 * @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 * 会启动完整 Spring Boot 应用上下文，并且真的启动一个内嵌 Web Server。
 * 端口不是 8080，而是测试运行时随机分配的端口，避免和本机正在运行的服务冲突。
 *
 * 因为这里已经有真实 HTTP 入口，所以不需要 @AutoConfigureMockMvc。
 * 请求也不再通过 MockMvc.perform(...) 发起，而是通过 Java HttpClient
 * 请求 http://localhost:{randomPort}，像真实客户端一样发 HTTP 请求。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthApiIntegrationTestWithEmbededWeb {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void seedUser() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(TestUsers.vincent());
    }

    @Test
    void loginValidateAndHealthFlowThroughEmbeddedWebServer() throws Exception {
        HttpResponse<String> loginResponse = postJson(
                "/api/v1/auth/login",
                """
                        {"username":"vincent","password":"123456"}
                        """
        );

        assertThat(loginResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.tokenType")).isEqualTo("Bearer");
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.username")).isEqualTo("vincent");
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.role")).isEqualTo("USER");

        String accessToken = JsonPath.read(loginResponse.body(), "$.accessToken");
        String refreshToken = JsonPath.read(loginResponse.body(), "$.refreshToken");
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        HttpResponse<String> validateResponse = getWithBearer("/api/v1/auth/validate", accessToken);

        assertThat(validateResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(JsonPath.<Boolean>read(validateResponse.body(), "$.valid")).isTrue();
        assertThat(JsonPath.<String>read(validateResponse.body(), "$.username")).isEqualTo("vincent");

        HttpResponse<String> healthResponse = get("/api/v1/auth/health");

        assertThat(healthResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(JsonPath.<String>read(healthResponse.body(), "$.status")).isEqualTo("UP");
    }

    @Test
    void invalidCredentialsReturn401ThroughEmbeddedWebServer() throws Exception {
        HttpResponse<String> response = postJson(
                "/api/v1/auth/login",
                """
                        {"username":"vincent","password":"wrong"}
                        """
        );

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(JsonPath.<String>read(response.body(), "$.message"))
                .isEqualTo("Invalid username or password");
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getWithBearer(String path, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
