package com.x.cloudnext.server.preview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.drive.*;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.next.*;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.link.DrivePublicLinkService;
import com.x.cloudnext.server.namespace.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DriveFilePreviewServiceTest {
    @TempDir Path root;
    DrivePreviewTicketService tickets=mock(DrivePreviewTicketService.class);
    DriveStorageService storage=mock(DriveStorageService.class);
    DriveItemRepository items=mock(DriveItemRepository.class);
    DriveVersionService versions=mock(DriveVersionService.class);
    DriveNamespaceService namespaces=mock(DriveNamespaceService.class);
    DriveScopeAccessService access=mock(DriveScopeAccessService.class);
    DrivePublicLinkService links=mock(DrivePublicLinkService.class);
    PreviewCache cache=mock(PreviewCache.class);
    com.x.cloudnext.server.audit.AuditLogService audit=mock(com.x.cloudnext.server.audit.AuditLogService.class);
    DriveFilePreviewService service;
    DriveItem item;DrivePreviewTicketPayload payload;AuthLoginResponse viewer;
    @BeforeEach void setup()throws Exception {
        DriveContext.bind(new DriveContext.State(new DriveDescriptor("drive","default","test"),"tenant","employee",1));
        item=new DriveItem();item.setId("item");item.setName("current.docx");item.setContentType("application/octet-stream");item.setStorageKey("current");
        item.setScopeType(DriveScopeType.PERSONAL);item.setScopeId("alice");item.setOwnerPersonOaId("alice");item.setItemType(DriveItemType.FILE);item.setParentId("");
        Path current=root.resolve("current.docx");Files.writeString(current,"synthetic");when(storage.load("current")).thenReturn(new FileSystemResource(current));when(items.findById("item")).thenReturn(Optional.of(item));
        payload=new DrivePreviewTicketPayload();payload.setDriveId("drive");payload.setItemId("item");payload.setViewerPersonOaIdRaw("alice");payload.setAllowDownload(false);payload.setExpiresAt(System.currentTimeMillis()+600000);
        when(tickets.parse("ticket")).thenReturn(payload);when(tickets.create(any())).thenReturn("new-ticket");
        viewer=new AuthLoginResponse();viewer.setPersonOaId("alice");when(namespaces.ticketViewer(payload)).thenReturn(viewer);when(access.requireItem(viewer,"item",DriveScopeAction.READ)).thenReturn(item);
        when(cache.status(any())).thenReturn(new PreviewCache.State());
        service=new DriveFilePreviewService(tickets,storage,items,versions,namespaces,access,links,cache,new CloudNextProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(service,"audit",audit);
    }
    @AfterEach void cleanup(){DriveContext.clear();}
    @Test void currentFileDescriptorBindsViewerNamespaceAndSourceVersion() {
        var response=service.describe(item,AccessContext.userWithPermissions(viewer,true,false,"read"),null,null);
        assertEquals("office",response.getKind());assertFalse(response.getAllowDownload());assertEquals("/api/previews/content",response.getPreviewUrl());
        ArgumentCaptor<DrivePreviewTicketPayload> ticket=ArgumentCaptor.forClass(DrivePreviewTicketPayload.class);verify(tickets).create(ticket.capture());
        assertEquals("alice",ticket.getValue().getViewerPersonOaIdRaw());assertNotNull(ticket.getValue().getSourceIdentity());assertFalse(ticket.getValue().isAllowEdit());
    }
    @Test void permissionsAreRecheckedBeforeEveryCacheLookup() {
        when(access.requireItem(viewer,"item",DriveScopeAction.READ)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"revoked"));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->service.inspect("ticket",false,false)).getStatus());verifyNoInteractions(cache);
    }
    @Test void permissionsAreRecheckedBeforeEveryContentRead() {
        when(access.requireItem(viewer,"item",DriveScopeAction.READ)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"revoked"));
        assertThrows(ResponseStatusException.class,()->service.content("ticket"));verifyNoInteractions(cache);
    }
    @Test void expiredOrRevokedPublicLinksCannotUseCachedArtifacts() {
        payload.setPublicLinkToken("link");payload.setPublicAccessTicket("password-ticket");
        when(links.requirePreviewItem("link","password-ticket")).thenThrow(new ResponseStatusException(HttpStatus.GONE,"revoked"));
        assertEquals(HttpStatus.GONE,assertThrows(ResponseStatusException.class,()->service.inspect("ticket",true,false)).getStatus());verifyNoInteractions(cache);
    }
    @Test void publicPasswordTicketIsCarriedForwardForRevalidation() {
        service.describe(item,AccessContext.publicLink("link",true,false),null,"password-ticket");
        ArgumentCaptor<DrivePreviewTicketPayload> ticket=ArgumentCaptor.forClass(DrivePreviewTicketPayload.class);verify(tickets).create(ticket.capture());
        assertEquals("password-ticket",ticket.getValue().getPublicAccessTicket());assertEquals("link",ticket.getValue().getPublicLinkToken());
    }
    @Test void historicalTypeAndSourceComeFromTheRequestedVersion()throws Exception {
        DriveItemVersion version=new DriveItemVersion();version.id="old";version.fileName="previous.pdf";version.contentType="application/pdf";version.storageKey="old";
        Path original=root.resolve("previous.pdf");Files.writeString(original,"%PDF-synthetic");when(storage.load("old")).thenReturn(new FileSystemResource(original));
        var response=service.describe(item,AccessContext.user(viewer),version,null);
        assertEquals("pdf",response.getKind());assertEquals("previous.pdf",response.getFileName());
        ArgumentCaptor<DrivePreviewTicketPayload> ticket=ArgumentCaptor.forClass(DrivePreviewTicketPayload.class);verify(tickets).create(ticket.capture());assertEquals("old",ticket.getValue().getVersionId());
    }
    @Test void historicalAccessKeepsTheExistingVersionPermissionGate()throws Exception {
        payload.setVersionId("old");when(versions.requireVersion(viewer,"item","old")).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"not writable"));
        assertThrows(ResponseStatusException.class,()->service.content("ticket"));verifyNoInteractions(cache);
    }
    @Test void sourceReplacementIsNotSilentlyServedUnderAnOldTicket() {
        payload.setSourceIdentity("previous-source");assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->service.inspect("ticket",false,false)).getStatus());verifyNoInteractions(cache);
    }
    @Test void deletedParentStopsPreviewEvenForAnUnexpiredTicket() {
        item.setParentId("parent");DriveItem parent=new DriveItem();parent.setId("parent");parent.setDeleted(true);when(items.findById("parent")).thenReturn(Optional.of(parent));
        assertThrows(ResponseStatusException.class,()->service.content("ticket"));verifyNoInteractions(cache);
    }
    @Test void readOnlyOfficeViewServesOnlyTheDerivedPdf()throws Exception {
        Path result=root.resolve("derivative.pdf");Files.writeString(result,"%PDF-test");when(cache.content(any())).thenReturn(result);
        var content=service.content("ticket");assertEquals("application/pdf",content.getContentType());assertEquals(result.toFile(),content.getResource().getFile());
    }
    @Test void retiredEditTicketsNeverStartConversion() {
        payload.setAllowEdit(true);assertEquals(HttpStatus.GONE,assertThrows(ResponseStatusException.class,()->service.inspect("ticket",true,false)).getStatus());verifyNoInteractions(cache);
    }
    @Test void unsafeMarkupIsNotAnImagePreview() {
        assertEquals("unsupported",PreviewSource.kind("file.svg","image/svg+xml"));assertEquals("text",PreviewSource.kind("file.md","text/html"));
    }
    @Test void rangeReadsKeepTheOriginalPreviewAuditWithoutDuplicatingEachChunk()throws Exception {
        Path result=root.resolve("derivative.pdf");Files.writeString(result,"%PDF-test");when(cache.content(any())).thenReturn(result);
        service.content("ticket");service.content("ticket");
        verify(audit,times(1)).recordAsync(eq("alice"),eq(com.x.cloudnext.server.audit.AuditAction.DOWNLOAD),eq("DRIVE_ITEM"),eq("item"),any(),any(),any(),any(),any(),any());
    }
    @Test void deniedReadsNeverWriteAPreviewAudit() {
        when(access.requireItem(viewer,"item",DriveScopeAction.READ)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"revoked"));
        assertThrows(ResponseStatusException.class,()->service.content("ticket"));verifyNoInteractions(audit);
    }
}
