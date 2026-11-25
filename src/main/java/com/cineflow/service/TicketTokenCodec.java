package com.cineflow.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * CineFlow v2 Cryptographic Ticket Token Codec.
 * Formats: CF1.<ticketId>.<nonce16_base64url>.<hmac16_base64url>
 * Employs HMAC-SHA256 with constant-time verification to reject forged tokens before DB access.
 */
public class TicketTokenCodec {

    private static final String PREFIX = "CF1";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64_ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

    /**
     * Issues a signed tamper-proof ticket token string.
     */
    public static String generateToken(int ticketId, int showtimeId, String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Secret key cannot be null or empty.");
        }

        byte[] nonceBytes = new byte[16];
        RANDOM.nextBytes(nonceBytes);
        String nonceStr = B64_ENC.encodeToString(nonceBytes);

        String payload = ticketId + "|" + showtimeId + "|" + nonceStr;
        byte[] signatureBytes = computeHmacTruncated(payload, secret);
        String sigStr = B64_ENC.encodeToString(signatureBytes);

        return PREFIX + "." + ticketId + "." + nonceStr + "." + sigStr;
    }

    /**
     * Verifies the cryptographic HMAC signature using constant-time comparison.
     */
    public static boolean verifySignature(String token, int showtimeId, String secret) {
        if (token == null || secret == null) return false;

        String[] parts = token.split("\\.");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }

        try {
            int ticketId = Integer.parseInt(parts[1]);
            String nonceStr = parts[2];
            byte[] providedSig = B64_DEC.decode(parts[3]);

            String payload = ticketId + "|" + showtimeId + "|" + nonceStr;
            byte[] expectedSig = computeHmacTruncated(payload, secret);

            // Constant-time comparison protects against timing attacks
            return MessageDigest.isEqual(expectedSig, providedSig);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the embedded ticket ID from a token.
     */
    public static int extractTicketId(String token) {
        if (token == null) return -1;
        String[] parts = token.split("\\.");
        if (parts.length >= 2 && PREFIX.equals(parts[0])) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    /**
     * Computes a SHA-256 hash string of the token for audit logging.
     */
    public static String hashToken(String token) {
        if (token == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(token.hashCode());
        }
    }

    private static byte[] computeHmacTruncated(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(keySpec);
            byte[] fullHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(fullHmac, 16); // 128-bit truncated HMAC
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
}
