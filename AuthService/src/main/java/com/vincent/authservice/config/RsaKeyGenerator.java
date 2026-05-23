package com.vincent.authservice.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

/**
 * Ensures RSA key material is available before JWT beans initialize.
 * <ul>
 *   <li>{@code file:} / filesystem paths — generates missing keys under that directory</li>
 *   <li>{@code classpath:} — read-only; keys must be packaged or provided via {@code scripts/generate-rsa-keys.sh}</li>
 * </ul>
 */
@Component
@Profile("!test")
public class RsaKeyGenerator {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyGenerator.class);
    private static final int KEY_SIZE = 2048;

    private final JwtProperties jwtProperties;
    private final JwtKeyLocationResolver keyLocationResolver;

    public RsaKeyGenerator(JwtProperties jwtProperties, JwtKeyLocationResolver keyLocationResolver) {
        this.jwtProperties = jwtProperties;
        this.keyLocationResolver = keyLocationResolver;
    }

    @PostConstruct
    public void init() throws IOException, NoSuchAlgorithmException {
        ensureKeysExist();
    }

    public void ensureKeysExist() throws IOException, NoSuchAlgorithmException {
        Optional<Path> privateKeyPath = keyLocationResolver.resolveWritablePath(jwtProperties.privateKeyPath());
        Optional<Path> publicKeyPath = keyLocationResolver.resolveWritablePath(jwtProperties.publicKeyPath());

        if (privateKeyPath.isPresent() != publicKeyPath.isPresent()) {
            throw new IllegalStateException(
                    "JWT private and public key locations must use the same scheme "
                            + "(both file-based or both classpath)"
            );
        }

        if (privateKeyPath.isPresent()) {
            generateFileKeysIfMissing(privateKeyPath.get(), publicKeyPath.get());
            return;
        }

        assertClasspathKeysPresent();
    }

    private void generateFileKeysIfMissing(Path privateKeyPath, Path publicKeyPath)
            throws IOException, NoSuchAlgorithmException {
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

    private void assertClasspathKeysPresent() {
        boolean privateExists = keyLocationResolver.exists(jwtProperties.privateKeyPath());
        boolean publicExists = keyLocationResolver.exists(jwtProperties.publicKeyPath());

        if (privateExists && publicExists) {
            log.info("Using classpath JWT keys (read-only)");
            return;
        }

        throw new IllegalStateException(
                "JWT keys not found on classpath. Run ./scripts/generate-rsa-keys.sh "
                        + "or set JWT_PRIVATE_KEY_PATH / JWT_PUBLIC_KEY_PATH to writable file: locations. "
                        + "Missing: "
                        + (privateExists ? "" : jwtProperties.privateKeyPath() + " ")
                        + (publicExists ? "" : jwtProperties.publicKeyPath())
        );
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
