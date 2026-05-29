package com.vincent.aiservice.config;

import com.vincent.aiservice.security.RsaPublicKeyLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfig {

    private final RsaPublicKeyLoader rsaPublicKeyLoader;
    private final JwtProperties jwtProperties;

    public JwtConfig(RsaPublicKeyLoader rsaPublicKeyLoader, JwtProperties jwtProperties) {
        this.rsaPublicKeyLoader = rsaPublicKeyLoader;
        this.jwtProperties = jwtProperties;
    }

    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaPublicKeyLoader.loadPublicKey()).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                JwtValidators.createDefaultWithIssuer(jwtProperties.issuer())
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
