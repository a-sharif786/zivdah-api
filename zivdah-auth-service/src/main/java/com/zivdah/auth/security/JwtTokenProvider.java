package com.zivdah.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    // Short-lived on purpose — clients renew it via POST /auth/refresh-token (see
    // RefreshTokenService) instead of holding a long-lived bearer token.
    @Value("${jwt.access-token-expiration:15m}")
    private Duration accessTokenExpiration;

    private Key key;

    @PostConstruct
    public void init() {
        key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId,
                                String mobile,
                                String role) {

        Date now = new Date();

        return Jwts.builder()
                .setSubject(mobile)
                .claim("userId", userId)
                .claim("role", role.toUpperCase())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessTokenExpiration.toMillis()))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {

        try {

            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);

            return true;

        } catch (Exception e) {

            // Expired tokens are now routine (15-min TTL), so no stack trace — just DEBUG.
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public long getAccessTokenExpirySeconds() {

        return accessTokenExpiration.toSeconds();
    }

    private Claims claims(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getUserIdFromToken(String token) {

        Object id = claims(token).get("userId");

        return id == null ? null : Long.valueOf(id.toString());
    }

    public String getMobileNumberFromToken(String token) {

        return claims(token).getSubject();
    }

    public String getRoleFromToken(String token) {

        return claims(token).get("role", String.class);
    }
}
//package com.zivdah.auth.security;
//
//import io.jsonwebtoken.JwtException;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.SignatureAlgorithm;
//import io.jsonwebtoken.security.Keys;
//import jakarta.annotation.PostConstruct;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//import java.nio.charset.StandardCharsets;
//import java.security.Key;
//import java.util.Date;
//
//@Component
//public class JwtTokenProvider {
//
//    @Value("${jwt.secret:my_super_secret_key_that_is_at_least_32_chars}")
//    private String secretKey;
//    private final long EXPIRATION_MS = 24 * 60 * 60 * 1000; // 1 day
//
//    private Key key;
//
//    @PostConstruct
//    public void init() {
//        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
//    }
//
//    // Generate token using userId, mobile number and role.
//    // Subject stays the mobile number for compatibility with other services'
//    // JwtAuthenticationFilter implementations, which read it as the principal.
//    public String generateToken(Long userId, String mobileNumber, String role) {
//        Date now = new Date();
//        Date expiryDate = new Date(now.getTime() + EXPIRATION_MS);
//
//        return Jwts.builder()
//                .setSubject(mobileNumber)           // mobile number as subject
//                .claim("userId", userId)
//                .claim("role", role)
//                .setIssuedAt(now)
//                .setExpiration(expiryDate)
//                .signWith(key, SignatureAlgorithm.HS256)
//                .compact();
//    }
//
//    // Validate token
//    public boolean validateToken(String token) {
//        try {
//            Jwts.parserBuilder()
//                    .setSigningKey(key)
//                    .build()
//                    .parseClaimsJws(token);
//            return true;
//        } catch (JwtException | IllegalArgumentException e) {
//            return false;
//        }
//    }
//
//    // Extract mobile number from token
//    public String getMobileNumberFromToken(String token) {
//        return Jwts.parserBuilder()
//                .setSigningKey(key)
//                .build()
//                .parseClaimsJws(token)
//                .getBody()
//                .getSubject();
//    }
//
//    // Extract role from token
//    public String getRoleFromToken(String token) {
//        return Jwts.parserBuilder()
//                .setSigningKey(key)
//                .build()
//                .parseClaimsJws(token)
//                .getBody()
//                .get("role", String.class);
//    }
//
//    // Extract userId from token
//    public Long getUserIdFromToken(String token) {
//        Object userId = Jwts.parserBuilder()
//                .setSigningKey(key)
//                .build()
//                .parseClaimsJws(token)
//                .getBody()
//                .get("userId");
//        return userId == null ? null : Long.valueOf(userId.toString());
//    }
//}
