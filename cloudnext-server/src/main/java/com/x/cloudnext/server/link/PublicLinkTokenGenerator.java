package com.x.cloudnext.server.link;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.x.cloudnext.server.drive.repository.DrivePublicLinkRepository;

/**
 * Generates the public-facing {@code token} that identifies a
 * {@code DrivePublicLink} on the {@code /public/links/{token}/...} endpoints.
 *
 * <p>Per Requirement 6.4 / 6.5 and design §"DrivePublicLink" 实体说明:
 * <ul>
 *   <li>Source of randomness: {@link SecureRandom} filling 24 bytes
 *       (192 bits of entropy — well above the 128-bit floor for an
 *       unguessable share secret).</li>
 *   <li>Encoding: {@link Base64#getUrlEncoder()} with padding stripped, so
 *       the token is URL-safe and exactly 32 characters wide.</li>
 *   <li>Collision handling: at most one retry. If the second draw also
 *       collides we treat it as a {@link SecureRandom} health issue and
 *       throw {@link IllegalStateException} rather than silently looping.</li>
 * </ul>
 *
 * <p>Note: this generator only produces the token string. Persistence and
 * the wider create-link flow are wired in task 2.4 ({@code DrivePublicLinkService}).
 */
@Component
public class PublicLinkTokenGenerator {

    /** 24 random bytes → 32 base64url characters (no padding). */
    private static final int TOKEN_BYTES = 24;

    private final DrivePublicLinkRepository drivePublicLinkRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public PublicLinkTokenGenerator(DrivePublicLinkRepository drivePublicLinkRepository) {
        this.drivePublicLinkRepository = drivePublicLinkRepository;
    }

    /**
     * Produce a fresh, unused public-link token.
     *
     * @return a 32-character base64url token guaranteed (modulo H2/MySQL
     *         visibility) to not collide with an existing
     *         {@code DrivePublicLink.token} at the moment of the lookup.
     * @throws IllegalStateException if two consecutive draws both collide
     *         with persisted tokens.
     */
    public String generate() {
        String token = nextToken();
        if (drivePublicLinkRepository.findByToken(token).isPresent()) {
            // Single retry, per design. A second collision on 192-bit randomness
            // points at a broken RNG or repository, not bad luck.
            token = nextToken();
            if (drivePublicLinkRepository.findByToken(token).isPresent()) {
                throw new IllegalStateException("public link token collision");
            }
        }
        return token;
    }

    private String nextToken() {
        byte[] buffer = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(buffer);
        return encoder.encodeToString(buffer);
    }
}
