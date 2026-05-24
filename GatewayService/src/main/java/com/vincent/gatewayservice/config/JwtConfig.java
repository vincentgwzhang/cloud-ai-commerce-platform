package com.vincent.gatewayservice.config;

import com.vincent.gatewayservice.security.RsaPublicKeyLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
public class JwtConfig {

    private final RsaPublicKeyLoader rsaPublicKeyLoader;
    private final JwtProperties jwtProperties;

    public JwtConfig(RsaPublicKeyLoader rsaPublicKeyLoader, JwtProperties jwtProperties) {
        this.rsaPublicKeyLoader = rsaPublicKeyLoader;
        this.jwtProperties = jwtProperties;
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() throws Exception {
        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withPublicKey(rsaPublicKeyLoader.loadPublicKey()).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                JwtValidators.createDefaultWithIssuer(jwtProperties.issuer())
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
