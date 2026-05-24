package com.vincent.authservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.Set;

/**
 * Skips JWT parsing on public auth endpoints so an stale {@code Authorization} header
 * (e.g. from Postman collection scripts) does not cause 401 before {@code /login}.
 */
public class PublicAuthBearerTokenResolver implements BearerTokenResolver {

    private static final Set<String> SKIP_BEARER_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/health"
    );

    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        if (SKIP_BEARER_PATHS.contains(request.getRequestURI())) {
            return null;
        }
        return delegate.resolve(request);
    }
}
