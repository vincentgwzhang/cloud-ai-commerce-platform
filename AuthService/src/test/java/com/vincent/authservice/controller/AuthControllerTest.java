package com.vincent.authservice.controller;

import com.vincent.authservice.dto.LoginRequest;
import com.vincent.authservice.dto.LoginResponse;
import com.vincent.authservice.dto.RefreshTokenRequest;
import com.vincent.authservice.dto.TokenValidationResponse;
import com.vincent.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 搭一个轻量级的 MVC 测试环境, 只拿 AuthController 出来测，不要启动整个项目
 * 
 * @WebMvcTest 测 Controller/Web MVC 层 默认就会提供 MockMvc
 * @AutoConfigureMockMvc 目的是 手动定制 MockMvc 配置， addFilters 的意思是说绕过Security 检查体制
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginDelegatesToService() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("token", "Bearer", 3600, "refresh", 604800, "vincent", "USER"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"vincent","password":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"))
                .andExpect(jsonPath("$.username").value("vincent"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void refreshRejectsBlankRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshDelegatesToService() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class)))
                .thenReturn(new LoginResponse("new-token", "Bearer", 3600, "new-refresh", 604800, "vincent", "USER"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-refresh"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));

        verify(authService).refresh(any(RefreshTokenRequest.class));
    }

    @Test
    void validateDelegatesToService() throws Exception {
        when(authService.validateToken(eq("Bearer abc")))
                .thenReturn(new TokenValidationResponse(true, "vincent", "USER"));

        mockMvc.perform(get("/api/v1/auth/validate")
                        .header("Authorization", "Bearer abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.username").value("vincent"));

        verify(authService).validateToken("Bearer abc");
    }

    @Test
    void healthReturnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/auth/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
