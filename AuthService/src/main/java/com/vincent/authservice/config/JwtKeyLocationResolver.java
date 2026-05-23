package com.vincent.authservice.config;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves JWT key locations from Spring resource URIs ({@code classpath:}, {@code file:}).
 */
@Component
public class JwtKeyLocationResolver {

    private final ResourceLoader resourceLoader;

    public JwtKeyLocationResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public boolean exists(String location) {
        Resource resource = resourceLoader.getResource(normalize(location));
        return resource.exists();
    }

    /**
     * Returns a filesystem path only when the location is file-based (writable).
     * Classpath and other non-file schemes return empty.
     */
    public Optional<Path> resolveWritablePath(String location) {
        String normalized = normalize(location);
        if (normalized.startsWith("classpath:")) {
            return Optional.empty();
        }
        try {
            Resource resource = resourceLoader.getResource(toFileLocation(normalized));
            if (!resource.isFile()) {
                return Optional.empty();
            }
            return Optional.of(resource.getFile().toPath().toAbsolutePath().normalize());
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid JWT key location: " + location, ex);
        }
    }

    private String normalize(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("JWT key location must not be blank");
        }
        return location.trim();
    }

    private String toFileLocation(String location) {
        if (location.startsWith("file:")) {
            return location;
        }
        return "file:" + location;
    }
}
