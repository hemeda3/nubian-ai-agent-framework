package com.nubian.ai.agentpress.util.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * Utility class for JWT (JSON Web Token) operations.
 * Handles parsing JWTs to extract claims like user ID.
 */
@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Extracts the user ID from a JWT token.
     * Assumes the user ID is stored in the 'sub' (subject) claim.
     *
     * @param token The JWT token (e.g., "Bearer <token>")
     * @return The user ID
     * @throws ResponseStatusException if the token is invalid or user ID cannot be extracted
     */
    public String getUserIdFromToken(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            logger.warn("Invalid or missing Authorization header format.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Authorization header format.");
        }

        String jwt = token.substring(7); // Remove "Bearer " prefix

        try {
            Jws<Claims> claimsJws = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(jwt);

            Claims claims = claimsJws.getBody();

            // Check expiration
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                logger.warn("JWT token has expired.");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token has expired.");
            }

            String userId = claims.getSubject(); // Assuming 'sub' claim holds the user ID
            if (userId == null || userId.isEmpty()) {
                logger.warn("User ID (subject) not found in JWT claims.");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID not found in token.");
            }
            logger.debug("Extracted user ID: {} from token.", userId);
            return userId;

        } catch (io.jsonwebtoken.security.SecurityException | io.jsonwebtoken.MalformedJwtException e) {
            logger.warn("Invalid JWT signature or malformed token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT token.", e);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            logger.warn("Expired JWT token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired JWT token.", e);
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            logger.warn("Unsupported JWT token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unsupported JWT token.", e);
        } catch (java.lang.IllegalArgumentException e) {
            logger.warn("JWT claims string is empty: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT claims string is empty.", e);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during JWT parsing: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing JWT token.", e);
        }
    }

    /**
     * Extracts the user ID from a JWT token if present and valid.
     * Returns an empty Optional if the token is missing, invalid, or expired.
     *
     * @param token The JWT token (e.g., "Bearer <token>")
     * @return An Optional containing the user ID if valid, otherwise empty.
     */
    public Optional<String> getOptionalUserIdFromHeader(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String jwt = token.substring(7); // Remove "Bearer " prefix

        try {
            Jws<Claims> claimsJws = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(jwt);

            Claims claims = claimsJws.getBody();

            // Check expiration
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                return Optional.empty();
            }

            String userId = claims.getSubject();
            if (userId == null || userId.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(userId);

        } catch (io.jsonwebtoken.security.SecurityException | io.jsonwebtoken.MalformedJwtException
                 | io.jsonwebtoken.ExpiredJwtException | io.jsonwebtoken.UnsupportedJwtException
                 | java.lang.IllegalArgumentException e) {
            // These exceptions indicate an invalid, expired, or malformed token,
            // or an empty claims string. We return empty Optional for these cases.
            logger.debug("Failed to extract optional user ID from token: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            // Catch any other unexpected exceptions and log them
            logger.error("An unexpected error occurred during optional JWT parsing: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
