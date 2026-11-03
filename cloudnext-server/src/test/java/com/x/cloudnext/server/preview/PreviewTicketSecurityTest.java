package com.x.cloudnext.server.preview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.drive.*;
import com.x.cloudnext.server.drive.entity.*;
import com.x.cloudnext.server.drive.repository.*;
import com.x.cloudnext.server.migration.repository.CloudNextMetaRepository;
import com.x.cloudnext.server.namespace.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class PreviewTicketSecurityTest {
    DriveNamespaceService namespaces=mock(DriveNamespaceService.class);
    DriveItemRepository items=mock(DriveItemRepository.class);
    DrivePublicLinkRepository links=mock(DrivePublicLinkRepository.class);
    DriveScopeAccessService access=mock(DriveScopeAccessService.class);
    DrivePreviewTicketService tickets;
    DrivePreviewTicketPayload payload;
    DriveItem item;
    @BeforeEach void setup() {
        DriveContext.bind(new DriveContext.State(new DriveDescriptor("drive","default","test"),"tenant","alice",1));
        tickets=new DrivePreviewTicketService(new CloudNextProperties(),new ObjectMapper(),mock(CloudNextMetaRepository.class));
        ReflectionTestUtils.setField(tickets,"namespaces",namespaces);ReflectionTestUtils.setField(tickets,"items",items);ReflectionTestUtils.setField(tickets,"links",links);ReflectionTestUtils.setField(tickets,"access",access);
        PreviewProperties preview=new PreviewProperties();preview.setSigningSecret("synthetic-preview-test-secret-only");ReflectionTestUtils.setField(tickets,"previewConfig",preview);
        item=new DriveItem();item.setId("item");when(items.findById("item")).thenReturn(Optional.of(item));
        payload=new DrivePreviewTicketPayload();payload.setItemId("item");payload.setViewerPersonOaIdRaw("alice");payload.setExpiresAt(Instant.now().plusSeconds(600).toEpochMilli());
        when(namespaces.ticketViewer(any())).thenReturn(new AuthLoginResponse());
    }
    @AfterEach void clear(){DriveContext.clear();}
    @Test void signsAndValidatesWithoutAnyOnlyofficeConfiguration() {
        var parsed=tickets.parse(tickets.create(payload));assertEquals("item",parsed.getItemId());assertEquals("drive",parsed.getDriveId());
        verify(namespaces).enterTicket("drive","tenant","alice",1);verify(access).requireItem(any(),eq("item"),eq(DriveScopeAction.READ));
    }
    @Test void modifiedSignatureIsRejectedBeforeAnyRepositoryRead() {
        String ticket=tickets.create(payload);assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,()->tickets.parse(ticket+"tampered")).getStatus());verifyNoInteractions(items);
    }
    @Test void expiredTicketIsRejectedBeforeAnyRepositoryRead() {
        payload.setExpiresAt(Instant.now().minusSeconds(1).toEpochMilli());String ticket=tickets.create(payload);
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,()->tickets.parse(ticket)).getStatus());verifyNoInteractions(items);
    }
    @Test void currentSharePermissionCanRevokeAPreviouslySignedGrant() {
        when(access.requireItem(any(),eq("item"),eq(DriveScopeAction.READ))).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"revoked"));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->tickets.parse(tickets.create(payload))).getStatus());
    }
    @Test void currentAccountCannotAdoptAnotherAccountsGrant() {
        String ticket=tickets.create(payload);DriveContext.clear();DriveContext.bind(new DriveContext.State(new DriveDescriptor("drive","default","test"),"tenant","bob",1));
        doAnswer(invocation->{DriveContext.bind(new DriveContext.State(new DriveDescriptor("drive","default","test"),"tenant","alice",1));return null;})
            .when(namespaces).enterTicket("drive","tenant","alice",1);
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->tickets.parse(ticket)).getStatus());
    }
    @Test void revokedPublicLinkCannotReuseAnOldGrant() {
        payload.setPublicLinkToken("link");DrivePublicLink link=mock(DrivePublicLink.class);when(link.getItemId()).thenReturn("item");when(link.isRevoked()).thenReturn(true);when(links.findByToken("link")).thenReturn(Optional.of(link));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->tickets.parse(tickets.create(payload))).getStatus());
    }
}
