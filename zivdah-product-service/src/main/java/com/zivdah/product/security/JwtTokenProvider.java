package com.zivdah.product.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final String SECRET_KEY = "my_super_secret_key_that_is_at_least_32_chars";
    private final long EXPIRATION_MS = 24 * 60 * 60 * 1000; // 1 day

    private final Key key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    // Generate token using mobile number
    public String generateToken(String mobileNumber) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .setSubject(mobileNumber)           // mobile number as subject
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Validate token
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Extract mobile number from token
    public String getMobileNumberFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
