package com.x.cloudnext.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.x.cloudnext.server.auth.entity.SsoBootstrap;
import com.x.cloudnext.server.auth.repository.SsoBootstrapRepository;
import com.x.cloudnext.server.config.CloudNextProperties;

/** Session and bootstrap are atomic; remote authentication and directory sync happen before this transaction. */
@Service
public class SsoSessionIssuer {
    private final AuthSessionService sessions;
    private final SsoBootstrapRepository bootstraps;
    private final CloudNextProperties properties;
    public SsoSessionIssuer(AuthSessionService sessions, SsoBootstrapRepository bootstraps, CloudNextProperties properties) {
        this.sessions = sessions; this.bootstraps = bootstraps; this.properties = properties;
    }
    @Transactional
    public SsoBootstrapService.BootstrapResult issue(AuthenticationResult principal, String returnPath) {
        AuthLoginResponse session = sessions.createSession(principal);
        String raw = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        SsoBootstrap bootstrap = new SsoBootstrap();
        bootstrap.setTokenHash(hash(raw)); bootstrap.setSessionToken(session.getToken());
        bootstrap.setReturnPath(returnPath);
        bootstrap.setExpiresAt(Instant.now().plusSeconds(properties.getAuth().getSsoBootstrapTtlSeconds()).toString());
        bootstraps.save(bootstrap);
        return new SsoBootstrapService.BootstrapResult(raw, returnPath);
    }
    private String hash(String value) {
        try {
            StringBuilder result = new StringBuilder();
            for (byte item : MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))
                result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }
}
