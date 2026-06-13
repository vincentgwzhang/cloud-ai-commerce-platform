package com.vincent.authservice.config;

import com.vincent.authservice.security.CustomUserDetailsService;
import com.vincent.authservice.security.PublicAuthBearerTokenResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 开始配置 Spring Security 的 HTTP 过滤器链。
        // 下面的链式调用都在修改同一个 HttpSecurity 对象，用来定义请求进入系统后的安全规则。
        http
                // 关闭 CSRF 防护。
                // CSRF 主要用于保护基于浏览器 Cookie 的登录会话；当前服务是无状态 REST API，
                // 主要依赖 Bearer Token/JWT，因此不需要 Spring Security 额外要求 CSRF token。
                .csrf(AbstractHttpConfigurer::disable)
                // 设置 Session 策略为 STATELESS，也就是无状态。
                // Spring Security 不会创建或使用 HTTP Session 来保存登录状态；
                // 每一次请求都必须自己携带认证信息，通常就是 JWT Bearer Token。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 把当前服务配置为 OAuth2 Resource Server。
                // Resource Server 的含义是：这个服务负责保护资源接口，并校验调用方带来的 access token。
                .oauth2ResourceServer(oauth2 -> oauth2
                        // 使用自定义 Bearer Token 解析器。
                        // 它决定 Spring Security 从请求的哪个位置读取 token，
                        // 也可以避免某些公开 auth 接口因为携带了 Authorization header 而误触发认证失败。
                        .bearerTokenResolver(new PublicAuthBearerTokenResolver())
                        // 启用 JWT 方式的 token 校验。
                        // Customizer.withDefaults() 表示使用 Spring Boot/Spring Security 的默认 JWT 配置，
                        // 例如从配置文件里的 issuer-uri 或 jwk-set-uri 找到公钥来验证 token 签名。
                        .jwt(Customizer.withDefaults()))
                // 配置 URL 级别的访问权限。
                // Spring Security 会按顺序匹配规则：先匹配下面列出的公开接口，再匹配最后的兜底规则。
                .authorizeHttpRequests(auth -> auth
                        // requestMatchers 里列出的路径全部允许匿名访问。
                        .requestMatchers(
                                // 登录接口必须公开，因为用户登录之前还没有 access token。
                                "/api/v1/auth/login",
                                // 刷新 token 的接口在过滤器层面放行，由 Controller 或业务逻辑自己校验 refresh token。
                                "/api/v1/auth/refresh",
                                // 健康检查接口公开，方便网关、部署平台或监控系统判断服务是否可用。
                                "/api/v1/auth/health",
                                // token 校验接口公开，方便其他服务调用 auth 服务来验证 token。
                                "/api/v1/auth/validate",
                                // Swagger UI 入口公开，方便开发人员查看接口文档。
                                "/swagger-ui.html",
                                // Swagger UI 所需的静态资源公开，否则页面本身可能能打开但资源加载失败。
                                "/swagger-ui/**",
                                // OpenAPI 文档 JSON 公开，Swagger UI 会读取这里生成接口文档。
                                "/v3/api-docs/**",
                                // actuator 健康检查公开，通常给容器编排、负载均衡或监控系统使用。
                                "/actuator/health",
                                "/actuator/health/**",
                                // actuator info 公开，用于暴露服务基础信息。
                                "/actuator/info",
                                // actuator metrics 公开，用于查看指标名称或单项指标。
                                "/actuator/metrics",
                                "/actuator/metrics/**",
                                // Prometheus 指标端点公开，方便 Prometheus 或兼容系统抓取监控指标。
                                "/actuator/prometheus"
                        ).permitAll()
                        // 除了上面 permitAll 的路径，其余任何请求都必须通过认证。
                        // 结合前面的 Resource Server 配置，这通常意味着请求必须携带有效的 JWT Bearer Token。
                        .anyRequest().authenticated()
                );
        // 根据上面的所有配置构建 SecurityFilterChain Bean。
        // Spring Security 会把这个过滤器链安装到 Web 应用中，实际拦截并保护 HTTP 请求。
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
