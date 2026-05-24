package com.vincent.inventoryservice.security;

import com.vincent.inventoryservice.config.JwtProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class RsaPublicKeyLoader {

    private final ResourceLoader resourceLoader;
    private final JwtProperties jwtProperties;

    public RsaPublicKeyLoader(ResourceLoader resourceLoader, JwtProperties jwtProperties) {
        this.resourceLoader = resourceLoader;
        this.jwtProperties = jwtProperties;
    }

    public RSAPublicKey loadPublicKey() throws Exception {
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(readKeyBytes(resolveKeyLocation())));
    }

    private String resolveKeyLocation() throws IOException {
        String configured = jwtProperties.publicKeyPath();
        if (resourceExists(configured)) {
            return configured;
        }
        List<String> tried = new ArrayList<>();
        tried.add(configured);
        for (String fallback : jwtProperties.publicKeyFallbackPaths()) {
            if (fallback.equals(configured)) {
                continue;
            }
            tried.add(fallback);
            if (resourceExists(fallback)) {
                return fallback;
            }
        }
        throw new IllegalStateException("""
                JWT public key not found. Tried: %s
                From working directory: %s
                Fix: run devops/script/local-dev-setup.sh
                Or set JWT_PUBLIC_KEY_PATH / app.jwt.public-key-path to a valid file location.
                """.formatted(tried, Path.of("").toAbsolutePath()));
    }

    private boolean resourceExists(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        if (resource.exists()) {
            return true;
        }
        if (location.startsWith("file:")) {
            Path path = Path.of(location.substring("file:".length()));
            return Files.isRegularFile(path);
        }
        return false;
    }

    private byte[] readKeyBytes(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
