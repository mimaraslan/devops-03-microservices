package com.mimaraslan.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JWT Token parse ve validation utility class
 * Keycloak JWT token'larından email ve username bilgilerini çıkarır
 */
@Slf4j
@Component
public class JwtTokenUtil {

    /**
     * JWT token'dan email bilgisini çıkarır
     * @param authToken "Bearer <token>" formatında veya sadece token
     * @return Email bilgisi veya empty
     */
    public Optional<String> extractEmail(String authToken) {
        try {
            if (authToken == null || authToken.isBlank()) {
                return Optional.empty();
            }

            // "Bearer " prefix'ini kaldır
            String token = authToken.startsWith("Bearer ") 
                ? authToken.substring(7) 
                : authToken;

            DecodedJWT decodedJWT = JWT.decode(token);
            String email = decodedJWT.getClaim("email").asString();
            
            if (email != null && !email.isBlank()) {
                log.debug("Email extracted from token: {}", email);
                return Optional.of(email);
            }
            
            log.warn("Email claim not found in token");
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error extracting email from token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * JWT token'dan username (preferred_username) bilgisini çıkarır
     * @param authToken "Bearer <token>" formatında veya sadece token
     * @return Username bilgisi veya empty
     */
    public Optional<String> extractUsername(String authToken) {
        try {
            if (authToken == null || authToken.isBlank()) {
                return Optional.empty();
            }

            // "Bearer " prefix'ini kaldır
            String token = authToken.startsWith("Bearer ") 
                ? authToken.substring(7) 
                : authToken;

            DecodedJWT decodedJWT = JWT.decode(token);
            
            // Önce preferred_username'e bak, yoksa username'e bak
            String username = decodedJWT.getClaim("preferred_username").asString();
            if (username == null || username.isBlank()) {
                username = decodedJWT.getClaim("username").asString();
            }
            
            if (username != null && !username.isBlank()) {
                log.debug("Username extracted from token: {}", username);
                return Optional.of(username);
            }
            
            log.warn("Username claim not found in token");
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error extracting username from token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * JWT token'dan hem email hem username bilgisini çıkarır
     * @param authToken "Bearer <token>" formatında veya sadece token
     * @return TokenInfo object veya empty
     */
    public Optional<TokenInfo> extractTokenInfo(String authToken) {
        Optional<String> email = extractEmail(authToken);
        Optional<String> username = extractUsername(authToken);
        
        if (email.isPresent() || username.isPresent()) {
            return Optional.of(new TokenInfo(
                email.orElse(null),
                username.orElse(null)
            ));
        }
        
        return Optional.empty();
    }

    /**
     * Token bilgilerini tutan inner class
     */
    public static class TokenInfo {
        private final String email;
        private final String username;

        public TokenInfo(String email, String username) {
            this.email = email;
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public String getUsername() {
            return username;
        }

        @Override
        public String toString() {
            return "TokenInfo{email='" + email + "', username='" + username + "'}";
        }
    }
}

