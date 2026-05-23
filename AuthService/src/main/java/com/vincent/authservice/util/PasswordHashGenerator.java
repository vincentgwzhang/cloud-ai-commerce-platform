package com.vincent.authservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility to print BCrypt hashes for SQL seed data.
 * Run: mvn -q exec:java -Dexec.mainClass=com.vincent.authservice.util.PasswordHashGenerator
 */
public final class PasswordHashGenerator {

    private PasswordHashGenerator() {
    }

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = args.length > 0 ? args[0] : "123456";
        System.out.println(encoder.encode(password));
    }
}
