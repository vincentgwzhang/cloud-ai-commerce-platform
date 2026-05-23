package com.vincent.authservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class RsaKeyGenerator {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyGenerator.class);
    private static final int KEY_SIZE = 2048;

    private final JwtProperties jwtProperties;

    public RsaKeyGenerator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void init() throws IOException, NoSuchAlgorithmException {
        ensureKeysExist();
    }

    public void ensureKeysExist() throws IOException, NoSuchAlgorithmException {
        Path privateKeyPath = resolveWritablePath(jwtProperties.privateKeyPath());
        Path publicKeyPath = resolveWritablePath(jwtProperties.publicKeyPath());

        if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
            log.info("RSA key pair already exists at {}", privateKeyPath.getParent());
            return;
        }

        Files.createDirectories(privateKeyPath.getParent());
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        writePem(privateKeyPath, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        writePem(publicKeyPath, "PUBLIC KEY", keyPair.getPublic().getEncoded());

        log.info("Generated RSA key pair at {}", privateKeyPath.getParent());
    }

    private Path resolveWritablePath(String classpathLocation) throws IOException {
        if (classpathLocation.startsWith("classpath:")) {
            String resourcePath = classpathLocation.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (resource.exists()) {
                try (InputStream ignored = resource.getInputStream()) {
                    return Path.of("src/main/resources").resolve(resourcePath);
                }
            }
            return Path.of("src/main/resources").resolve(resourcePath);
        }
        return Path.of(classpathLocation);
    }

    private void writePem(Path path, String type, byte[] encoded) throws IOException {
        String base64 = Base64.getMimeEncoder(64, System.lineSeparator().getBytes())
                .encodeToString(encoded);
        String pem = "-----BEGIN " + type + "-----" + System.lineSeparator()
                + base64 + System.lineSeparator()
                + "-----END " + type + "-----" + System.lineSeparator();
        Files.writeString(path, pem);
    }
}
