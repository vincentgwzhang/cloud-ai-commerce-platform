package com.vincent.productservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

class RsaPublicKeyLoaderTest {

    @Test
    void loadsPublicKeyFromClasspath() throws Exception {
        JwtProperties properties = new JwtProperties("auth-service-test", "classpath:keys/public.pem");
        RsaPublicKeyLoader loader = new RsaPublicKeyLoader(new DefaultResourceLoader(), properties);

        RSAPublicKey publicKey = loader.loadPublicKey();

        assertThat(publicKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(publicKey.getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
    }

    @Test
    void loadsPublicKeyFromFilePath(@TempDir Path tempDir) throws Exception {
        Path keyFile = tempDir.resolve("public.pem");
        try (var in = getClass().getClassLoader().getResourceAsStream("keys/public.pem")) {
            assertThat(in).isNotNull();
            Files.write(keyFile, in.readAllBytes());
        }

        String location = "file:" + keyFile.toAbsolutePath();
        JwtProperties properties = new JwtProperties("auth-service-test", location);
        RsaPublicKeyLoader loader = new RsaPublicKeyLoader(new DefaultResourceLoader(), properties);

        RSAPublicKey publicKey = loader.loadPublicKey();

        assertThat(publicKey.getAlgorithm()).isEqualTo("RSA");
    }

}
