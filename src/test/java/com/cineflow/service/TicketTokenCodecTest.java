package com.cineflow.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketTokenCodecTest {

    private final String secret = "test-secret-key-32b-hmac-sha256!";
    private final int ticketId = 142;
    private final int showtimeId = 5;

    @Test
    @DisplayName("Should generate valid tamper-proof token format")
    void testTokenGeneration() {
        String token = TicketTokenCodec.generateToken(ticketId, showtimeId, secret);
        assertNotNull(token);
        assertTrue(token.startsWith("CF1."));

        String[] parts = token.split("\\.");
        assertEquals(4, parts.length);
        assertEquals("CF1", parts[0]);
        assertEquals(String.valueOf(ticketId), parts[1]);
        assertFalse(parts[2].isEmpty());
        assertFalse(parts[3].isEmpty());
    }

    @Test
    @DisplayName("Should verify valid signature successfully")
    void testValidSignature() {
        String token = TicketTokenCodec.generateToken(ticketId, showtimeId, secret);
        boolean valid = TicketTokenCodec.verifySignature(token, showtimeId, secret);
        assertTrue(valid, "Legitimate token must pass signature verification");
    }

    @Test
    @DisplayName("Should detect and reject tampered token signatures")
    void testTamperDetection() {
        String token = TicketTokenCodec.generateToken(ticketId, showtimeId, secret);
        String[] parts = token.split("\\.");
        assertEquals(4, parts.length);

        // 1. Tamper signature (flip first char)
        String tamperedSig = (parts[3].charAt(0) == 'A' ? "B" : "A") + parts[3].substring(1);
        String tamperedToken1 = parts[0] + "." + parts[1] + "." + parts[2] + "." + tamperedSig;
        assertFalse(TicketTokenCodec.verifySignature(tamperedToken1, showtimeId, secret),
                "Tampered signature must be rejected");

        // 2. Tamper ticket id
        String tamperedToken2 = parts[0] + "." + (ticketId + 1) + "." + parts[2] + "." + parts[3];
        assertFalse(TicketTokenCodec.verifySignature(tamperedToken2, showtimeId, secret),
                "Tampered ticket ID must be rejected");

        // 3. Tamper nonce
        String tamperedNonce = (parts[2].charAt(0) == 'X' ? "Y" : "X") + parts[2].substring(1);
        String tamperedToken3 = parts[0] + "." + parts[1] + "." + tamperedNonce + "." + parts[3];
        assertFalse(TicketTokenCodec.verifySignature(tamperedToken3, showtimeId, secret),
                "Tampered nonce must be rejected");
    }

    @Test
    @DisplayName("Should reject token when verified with wrong showtime or wrong secret")
    void testWrongParameters() {
        String token = TicketTokenCodec.generateToken(ticketId, showtimeId, secret);

        // Wrong showtime ID
        assertFalse(TicketTokenCodec.verifySignature(token, 999, secret));

        // Wrong secret key
        assertFalse(TicketTokenCodec.verifySignature(token, showtimeId, "different-secret-key-12345678!"));
    }

    @Test
    @DisplayName("Should extract correct ticketId from token")
    void testExtractTicketId() {
        String token = TicketTokenCodec.generateToken(9876, showtimeId, secret);
        assertEquals(9876, TicketTokenCodec.extractTicketId(token));
        assertEquals(-1, TicketTokenCodec.extractTicketId("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Should compute 64-character SHA-256 hash of token")
    void testTokenHash() {
        String token = TicketTokenCodec.generateToken(ticketId, showtimeId, secret);
        String hash = TicketTokenCodec.hashToken(token);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
}
