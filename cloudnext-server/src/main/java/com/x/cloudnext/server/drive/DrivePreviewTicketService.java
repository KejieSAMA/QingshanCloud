package com.x.cloudnext.server.drive;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.migration.repository.CloudNextMetaRepository;

@Service
public class DrivePreviewTicketService {

    private final CloudNextProperties properties;
    private final ObjectMapper objectMapper;
    private final String fallbackSigningSecret = UUID.randomUUID().toString();
    private final CloudNextMetaRepository metaRepository;

    public DrivePreviewTicketService(CloudNextProperties properties, ObjectMapper objectMapper,CloudNextMetaRepository metaRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metaRepository = metaRepository;
    }

    public String create(DrivePreviewTicketPayload payload) {
        try {
            if(payload.getIssuedAt()<=0L)payload.setIssuedAt(Instant.now().toEpochMilli());
            String body = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            return body + "." + sign(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cloudnext drive failed to serialize preview ticket", e);
        } catch (Exception e) {
            throw new IllegalStateException("cloudnext drive failed to sign preview ticket", e);
        }
    }

    public DrivePreviewTicketPayload parse(String ticket) {
        if (!StringUtils.hasText(ticket) || !ticket.contains(".")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "preview ticket is invalid");
        }
        String[] parts = ticket.split("\\.", 2);
        String body = parts[0];
        String signature = parts[1];
        try {
            byte[] expected = sign(body).getBytes(StandardCharsets.UTF_8);
            byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "preview ticket signature is invalid");
            }
            DrivePreviewTicketPayload payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(body),
                    DrivePreviewTicketPayload.class);
            if (payload.getExpiresAt() <= Instant.now().toEpochMilli()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "preview ticket has expired");
            }
            long notBefore=metaRepository.findByMetaKey("onlyoffice_ticket_not_before_epoch_ms").map(meta->{try{return Long.parseLong(meta.getMetaValue());}catch(Exception e){return 0L;}}).orElse(0L);
            if(payload.getIssuedAt()<=notBefore)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"preview ticket was issued before the migration cutover");
            return payload;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "preview ticket is invalid", e);
        }
    }

    private String sign(String body) throws Exception {
        String secret = requireSigningSecret();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String requireSigningSecret() {
        String secret = properties.getDrive().getOnlyoffice().getJwtSecret();
        if (!StringUtils.hasText(secret)) {
            return fallbackSigningSecret;
        }
        return secret;
    }
}
