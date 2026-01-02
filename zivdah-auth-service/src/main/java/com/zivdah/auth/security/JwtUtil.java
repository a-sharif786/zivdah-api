package com.zivdah.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // Make sure this is at least 32 characters (256 bits)
    private final String SECRET_KEY = "my_super_secret_key_that_is_at_least_32_chars";

    private final long EXPIRATION_TIME = 1000 * 60 * 60 * 24; // 24 hour

    // Convert string to secure SecretKey
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String mobile) {
        return Jwts.builder()
                .setSubject(mobile)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // Use Key object
                .compact();
    }
}
