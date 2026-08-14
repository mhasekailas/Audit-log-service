package com.schwab.auditlog.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for cryptographic hash operations.
 * Uses SHA-256 for hash chain verification.
 */
@Component
public class HashUtil {
    
    private static final String ALGORITHM = "SHA-256";
    private static final String GENESIS_HASH = hashString("GENESIS");
    private final ObjectMapper objectMapper;
    
    public HashUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Compute SHA-256 hash of a string
     */
    public static String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Compute content hash for an audit event
     * Combines: eventType + actorId + resourceType + resourceId + payload + timestamp
     */
    public String computeContentHash(String eventType, String actorId, String resourceType,
                                     String resourceId, JsonNode payload, String timestamp) {
        String content = String.format(
            "%s|%s|%s|%s|%s|%s",
            eventType, actorId, resourceType, resourceId,
            payload.toString(), timestamp
        );
        return hashString(content);
    }
    
    /**
     * Compute chain hash: SHA-256(previousChainHash + currentContentHash)
     */
    public String computeChainHash(String previousChainHash, String currentContentHash) {
        String combined = previousChainHash + currentContentHash;
        return hashString(combined);
    }
    
    /**
     * Get the genesis hash for the first record
     */
    public String getGenesisHash() {
        return GENESIS_HASH;
    }
    
    /**
     * Convert bytes to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    /**
     * Verify a content hash is correct
     */
    public boolean verifyContentHash(String eventType, String actorId, String resourceType,
                                     String resourceId, JsonNode payload, String timestamp,
                                     String expectedHash) {
        String computedHash = computeContentHash(eventType, actorId, resourceType,
                                                  resourceId, payload, timestamp);
        return computedHash.equals(expectedHash);
    }
    
    /**
     * Verify a chain hash is correct
     */
    public boolean verifyChainHash(String previousChainHash, String contentHash,
                                   String expectedChainHash) {
        String computedHash = computeChainHash(previousChainHash, contentHash);
        return computedHash.equals(expectedChainHash);
    }
}
