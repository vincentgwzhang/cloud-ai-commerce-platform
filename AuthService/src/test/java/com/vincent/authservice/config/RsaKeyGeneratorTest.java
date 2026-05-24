package com.vincent.authservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RsaKeyGeneratorTest {

    @Mock
    private JwtKeyLocationResolver keyLocationResolver;

    private RsaKeyGenerator generator;

    @TempDir
    Path tempDir;

    @Test
    void generatesFileKeysWhenMissingAndSkipsWhenPresent() throws Exception {
        Path privateKey = tempDir.resolve("private.pem");
        Path publicKey = tempDir.resolve("public.pem");
        JwtProperties properties = properties(privateKey, publicKey);

        generator = new RsaKeyGenerator(properties, keyLocationResolver);

        when(keyLocationResolver.resolveWritablePath(privateKey.toString())).thenReturn(Optional.of(privateKey));
        when(keyLocationResolver.resolveWritablePath(publicKey.toString())).thenReturn(Optional.of(publicKey));

        generator.ensureKeysExist();
        assertThat(Files.exists(privateKey)).isTrue();
        assertThat(Files.exists(publicKey)).isTrue();
        assertThat(Files.readString(privateKey)).contains("BEGIN PRIVATE KEY");

        generator.ensureKeysExist();
    }

    @Test
    void usesClasspathKeysWhenPresent() throws Exception {
        JwtProperties properties = properties("classpath:keys/private.pem", "classpath:keys/public.pem");
        generator = new RsaKeyGenerator(properties, keyLocationResolver);

        when(keyLocationResolver.resolveWritablePath("classpath:keys/private.pem")).thenReturn(Optional.empty());
        when(keyLocationResolver.resolveWritablePath("classpath:keys/public.pem")).thenReturn(Optional.empty());
        when(keyLocationResolver.exists("classpath:keys/private.pem")).thenReturn(true);
        when(keyLocationResolver.exists("classpath:keys/public.pem")).thenReturn(true);

        generator.ensureKeysExist();
    }

    @Test
    void failsWhenClasspathKeysMissing() {
        JwtProperties properties = properties("classpath:keys/private.pem", "classpath:keys/public.pem");
        generator = new RsaKeyGenerator(properties, keyLocationResolver);

        when(keyLocationResolver.resolveWritablePath("classpath:keys/private.pem")).thenReturn(Optional.empty());
        when(keyLocationResolver.resolveWritablePath("classpath:keys/public.pem")).thenReturn(Optional.empty());
        when(keyLocationResolver.exists("classpath:keys/private.pem")).thenReturn(false);
        when(keyLocationResolver.exists("classpath:keys/public.pem")).thenReturn(false);

        assertThatThrownBy(() -> generator.ensureKeysExist())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT keys not found on classpath");
    }

    @Test
    void rejectsMixedKeyLocationSchemes() {
        JwtProperties properties = properties("classpath:keys/private.pem", "file:/tmp/public.pem");
        generator = new RsaKeyGenerator(properties, keyLocationResolver);

        when(keyLocationResolver.resolveWritablePath("classpath:keys/private.pem")).thenReturn(Optional.empty());
        when(keyLocationResolver.resolveWritablePath("file:/tmp/public.pem")).thenReturn(Optional.of(Path.of("/tmp/public.pem")));

        assertThatThrownBy(() -> generator.ensureKeysExist())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same scheme");
    }

    private static JwtProperties properties(Path privateKey, Path publicKey) {
        return new JwtProperties("test-issuer", 3600L, 604800L, privateKey.toString(), publicKey.toString());
    }

    private static JwtProperties properties(String privateKey, String publicKey) {
        return new JwtProperties("test-issuer", 3600L, 604800L, privateKey, publicKey);
    }
}
