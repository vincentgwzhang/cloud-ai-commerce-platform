package com.vincent.authservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyLocationResolverTest {

    @TempDir
    Path tempDir;

    private JwtKeyLocationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new JwtKeyLocationResolver(new DefaultResourceLoader());
    }

    @Test
    void resolvesWritableFilePath() throws Exception {
        Path keyFile = tempDir.resolve("private.pem");
        Files.writeString(keyFile, "test");

        assertThat(resolver.resolveWritablePath("file:" + keyFile.toAbsolutePath()))
                .contains(keyFile.toAbsolutePath().normalize());
        assertThat(resolver.exists("file:" + keyFile.toAbsolutePath())).isTrue();
    }

    @Test
    void resolvesRelativeFilePath() {
        assertThat(resolver.resolveWritablePath("file:../devops/data/keys/private.pem"))
                .isPresent();
    }

    @Test
    void classpathIsNotWritable() {
        assertThat(resolver.resolveWritablePath("classpath:keys/private.pem")).isEmpty();
    }

    @Test
    void classpathExistsForTestResources() {
        assertThat(resolver.exists("classpath:keys/private.pem")).isTrue();
        assertThat(resolver.exists("classpath:keys/public.pem")).isTrue();
    }

    @Test
    void rejectsBlankLocation() {
        assertThatThrownBy(() -> resolver.resolveWritablePath("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
