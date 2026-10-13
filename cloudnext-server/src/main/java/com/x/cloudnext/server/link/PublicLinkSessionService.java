package com.x.cloudnext.server.link;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Issues and verifies short-lived {@code accessTicket}s for public-link
 * verified sessions (Requirement 8.3 / Design §"DrivePublicLinkService"
 * accessTicket).
 *
 * <p>Ticket format ({@code .} separated, both halves base64url, no padding):
 * <pre>
 *   accessTicket := b64url(payload) + "." + b64url(HMAC-SHA256(payload))
 *   payload      := token + "." + expEpochSeconds + "." + iatEpochSeconds
 * </pre>
 *
 * <p>Secret material comes from {@code cloudnext.public-link.session-secret};
 * if blank, a fresh 32-byte key is generated at startup and used for the
 * lifetime of this JVM (will not survive a restart).
 *
 * <p>The TTL comes from {@code cloudnext.public-link.session-ttl-seconds}
 * (default 1800).
 *
 * <p>This service never logs secrets or ticket strings.
 */
@Service
public class PublicLinkSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicLinkSessionService.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int EPHEMERAL_KEY_BYTES = 32;

    private final String configuredSecret;
    private final long ttlSeconds;
    private final Clock clock;

    private byte[] secretBytes;

    @Autowired
    public PublicLinkSessionService(
            @Value("${cloudnext.public-link.session-secret:}") String configuredSecret,
            @Value("${cloudnext.public-link.session-ttl-seconds:1800}") long ttlSeconds) {
        this(configuredSecret, ttlSeconds, Clock.systemUTC());
    }

    /** Test-only constructor: injectable {@link Clock}. */
    PublicLinkSessionService(String configuredSecret, long ttlSeconds, Clock clock) {
        this.configuredSecret = configuredSecret;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    @PostConstruct
    void initSecret() {
        if (!StringUtils.hasText(configuredSecret)) {
            byte[] generated = new byte[EPHEMERAL_KEY_BYTES];
            new SecureRandom().nextBytes(generated);
            this.secretBytes = generated;
            LOGGER.info("cloudnext.public-link.session-secret not configured; "
                    + "generated ephemeral key for this JVM (will not survive restart)");
        } else {
            this.secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        // Fail fast if HMAC-SHA256 is unavailable (should not happen on JDK 11+).
        try {
            Mac.getInstance(HMAC_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HMAC_ALGORITHM + " is not available on this JVM", e);
        }
    }

    /**
     * Issue a fresh {@code accessTicket} bound to the given public-link token.
     *
     * @param token the public-link token (must be non-blank)
     * @return signed, url-safe ticket string
     */
    public String issue(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("token must not be blank");
        }
        long iat = Instant.now(clock).getEpochSecond();
        long exp = iat + ttlSeconds;
        String payload = token + "." + exp + "." + iat;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(payloadBytes);
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(payloadBytes) + "." + enc.encodeToString(signature);
    }

    /**
     * Verify a previously-issued {@code accessTicket} for a given token.
     *
     * @param token        the public-link token to match against the ticket payload
     * @param accessTicket the ticket presented by the caller
     * @return {@code true} only if the ticket is well-formed, signed by this
     *         service, not expired, and its payload token equals the supplied
     *         {@code token}
     */
    public boolean verify(String token, String accessTicket) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(accessTicket)) {
            return false;
        }
        String[] parts = accessTicket.split("\\.", -1);
        if (parts.length != 2) {
            return false;
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            signature = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            return false;
        }

        byte[] expectedSignature = sign(payloadBytes);
        // Constant-time comparison to avoid timing attacks.
        if (!MessageDigest.isEqual(expectedSignature, signature)) {
            return false;
        }

        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        String[] payloadParts = payload.split("\\.", -1);
        if (payloadParts.length != 3) {
            return false;
        }
        String tokenInTicket = payloadParts[0];
        long exp;
        try {
            exp = Long.parseLong(payloadParts[1]);
            // iat is not used for validation but must be parseable.
            Long.parseLong(payloadParts[2]);
        } catch (NumberFormatException e) {
            return false;
        }

        if (!tokenInTicket.equals(token)) {
            return false;
        }
        long now = Instant.now(clock).getEpochSecond();
        if (now > exp) {
            return false;
        }
        return true;
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HMAC_ALGORITHM + " is not available on this JVM", e);
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalStateException("invalid HMAC key for " + HMAC_ALGORITHM, e);
        }
    }
}
