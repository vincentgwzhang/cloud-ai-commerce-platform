package com.vincent.orderservice.security;

import com.vincent.orderservice.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.security.interfaces.RSAPublicKey;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RsaPublicKeyLoaderTest {

    @Test
    void loadsPublicKeyFromClasspath() throws Exception {
        JwtProperties properties = new JwtProperties("auth-service-test", "classpath:keys/public.pem", List.of());
        RsaPublicKeyLoader loader = new RsaPublicKeyLoader(new DefaultResourceLoader(), properties);
        RSAPublicKey key = loader.loadPublicKey();
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
    }
}
