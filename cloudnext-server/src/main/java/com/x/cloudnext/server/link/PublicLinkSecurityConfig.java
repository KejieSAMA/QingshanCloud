package com.x.cloudnext.server.link;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Crypto wiring for the public-link iteration (Requirement 6.5 / design
 * §"DrivePublicLink" 实体说明).
 *
 * <p>Exposes a single {@link BCryptPasswordEncoder} bean used by
 * {@code DrivePublicLinkService} (task 2.4) to hash the optional access
 * password supplied when creating or updating a {@code DrivePublicLink}.
 *
 * <p>Scope intentionally limited to the password encoder. We rely on the
 * {@code spring-security-crypto} module only — the full
 * {@code spring-boot-starter-security} is deliberately not on the
 * classpath, so this configuration does not register any auth filter
 * chain or override CloudNext's existing {@code AuthSessionService}-based
 * session management.
 *
 * <p>Strength is left at {@link BCryptPasswordEncoder}'s default of 10,
 * matching common Spring Security practice and giving sub-200ms hashing on
 * the target hardware.
 */
@Configuration
public class PublicLinkSecurityConfig {

    /**
     * BCrypt encoder for {@code DrivePublicLink.passwordHash}.
     *
     * <p>The "no password" semantics for a link are represented by an
     * empty string in {@code passwordHash}; the encoder is only invoked
     * when the user actually sets a password. Plaintext passwords MUST
     * NOT be stored.
     */
    @Bean
    public BCryptPasswordEncoder publicLinkPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
