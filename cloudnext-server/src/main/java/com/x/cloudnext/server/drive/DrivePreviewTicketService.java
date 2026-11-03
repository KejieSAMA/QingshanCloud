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
    @org.springframework.beans.factory.annotation.Autowired private com.x.cloudnext.server.namespace.DriveNamespaceService namespaces;
    @org.springframework.beans.factory.annotation.Autowired private com.x.cloudnext.server.drive.repository.DriveItemRepository items;
    @org.springframework.beans.factory.annotation.Autowired private com.x.cloudnext.server.drive.repository.DrivePublicLinkRepository links;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy private DriveScopeAccessService access;

    private final CloudNextProperties properties;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired private com.x.cloudnext.server.preview.PreviewProperties previewConfig;
    private final String fallbackSigningSecret = UUID.randomUUID().toString();
    private final CloudNextMetaRepository metaRepository;

    public DrivePreviewTicketService(CloudNextProperties properties, ObjectMapper objectMapper,CloudNextMetaRepository metaRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metaRepository = metaRepository;
    }

    public String create(DrivePreviewTicketPayload payload) {
        try {
            com.x.cloudnext.server.namespace.DriveContext.State context = com.x.cloudnext.server.namespace.DriveContext.current();
            if (context == null) throw new IllegalStateException("drive context required for preview ticket");
            if (payload.getDriveId() != null) com.x.cloudnext.server.namespace.DriveContext.assertSame(payload.getDriveId());
            payload.setDriveId(context.drive.getId()); payload.setTenantId(context.tenantId);
            payload.setFormEmployeeId(context.employeeId); payload.setAuthVersion(context.authVersion);
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
            if (!StringUtils.hasText(payload.getDriveId())) {
                // Legacy tickets can only resolve to a migrated default-drive item.
                var item = items.findById(payload.getItemId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found"));
                var namespace = namespacesForLegacy(item.getDriveId());
                payload.setDriveId(namespace);
            }
            namespaces.enterTicket(payload.getDriveId(), payload.getTenantId(), payload.getFormEmployeeId(), payload.getAuthVersion());
            var item = items.findById(payload.getItemId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found"));
            if (item.isDeleted()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found");
            if (StringUtils.hasText(payload.getPublicLinkToken())) {
                var link = links.findByToken(payload.getPublicLinkToken()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "link not found"));
                if (!item.getId().equals(link.getItemId()) || link.isRevoked() || (StringUtils.hasText(link.getExpiresAt()) && !Instant.parse(link.getExpiresAt()).isAfter(Instant.now()))) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "link no longer permits preview");
                }
                if (link.getPermission() != PublicLinkPermission.DOWNLOAD) payload.setAllowDownload(false);
            } else if (StringUtils.hasText(payload.getViewerPersonOaIdRaw())) {
                access.requireItem(namespaces.ticketViewer(payload), item.getId(), payload.isAllowEdit() ? DriveScopeAction.WRITE : DriveScopeAction.READ);
            } else if (!com.x.cloudnext.server.namespace.DriveContext.current().drive.isDefault()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "preview viewer is missing");
            }
            return payload;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "preview ticket is invalid", e);
        }
    }

    private String namespacesForLegacy(String driveId) {
        namespaces.enterLegacyTicket(driveId);
        return driveId;
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
        String secret = previewConfig == null ? "" : previewConfig.getSigningSecret();
        // Read the legacy key only to keep pre-cutover signed viewer links verifiable. No editor service is used.
        if (!StringUtils.hasText(secret)) secret = properties.getDrive().getOnlyoffice().getJwtSecret();
        if (!StringUtils.hasText(secret)) {
            return fallbackSigningSecret;
        }
        return secret;
    }
}
