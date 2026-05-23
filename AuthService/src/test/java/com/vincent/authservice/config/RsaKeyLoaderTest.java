package com.vincent.authservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RsaKeyLoaderTest {

    @Autowired
    private RsaKeyLoader rsaKeyLoader;

    @Test
    void loadsRsaKeyPairFromClasspath() throws Exception {
        RSAPrivateKey privateKey = rsaKeyLoader.loadPrivateKey();
        RSAPublicKey publicKey = rsaKeyLoader.loadPublicKey();

        assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(publicKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(privateKey.getModulus()).isEqualTo(publicKey.getModulus());
    }
}
