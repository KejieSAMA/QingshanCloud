package com.x.cloudnext.server.drive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.auth.*;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.next.DriveVersionService;

class NativeFileTicketsTest {
    final AuthSessionService sessions=mock(AuthSessionService.class);
    final DriveService drive=mock(DriveService.class);
    final DriveVersionService versions=mock(DriveVersionService.class);
    final DriveZipPackagingService zip=mock(DriveZipPackagingService.class);
    final AtomicLong now=new AtomicLong(1000);
    final Clock clock=mock(Clock.class);
    NativeFileTickets tickets;
    DriveItem item;
    @BeforeEach void setup(){
        when(clock.millis()).thenAnswer(i->now.get());
        when(sessions.currentSession("private-session")).thenReturn(new AuthLoginResponse());
        item=mock(DriveItem.class);when(item.getId()).thenReturn("file-1");when(item.getItemType()).thenReturn(DriveItemType.FILE);
        when(item.getStorageKey()).thenReturn("frozen-content");when(item.getName()).thenReturn("文件.txt");when(item.getContentType()).thenReturn("text/plain");when(item.getSizeBytes()).thenReturn(12L);
        when(drive.requireDownloadItem("private-session","file-1")).thenReturn(item);
        tickets=new NativeFileTickets(sessions,drive,versions,zip,clock);
    }
    NativeFileTickets.Request input(){var request=new NativeFileTickets.Request();request.itemIds=List.of("file-1");request.action="SAVE";return request;}
    @Test void issueChecksPermissionWithoutReadingContentOrAuditAndDoesNotExposeSession() throws Exception {
        var grant=tickets.issue("private-session",input());
        assertTrue(grant.ticket.matches("[a-f0-9]{64}"));assertSame(grant,tickets.require(grant.ticket));
        verify(drive,never()).downloadItem(anyString(),anyString());
        String json=new ObjectMapper().writeValueAsString(grant);
        assertFalse(json.contains("private-session"));assertFalse(json.contains("frozen-content"));assertFalse(json.contains("itemIds"));
    }
    @Test void expiredTicketIsRejected(){var grant=tickets.issue("private-session",input());now.set(301001);assertThrows(ResponseStatusException.class,()->tickets.require(grant.ticket));}
    @Test void accountRevocationRejectsExistingTicket(){var grant=tickets.issue("private-session",input());when(sessions.currentSession(anyString())).thenReturn(null);assertThrows(ResponseStatusException.class,()->tickets.require(grant.ticket));}
    @Test void permissionRevocationRejectsExistingTicket(){var grant=tickets.issue("private-session",input());when(drive.requireDownloadItem(anyString(),anyString())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));assertThrows(ResponseStatusException.class,()->tickets.require(grant.ticket));}
    @Test void changedFileCannotReuseFrozenTicket(){var grant=tickets.issue("private-session",input());when(item.getStorageKey()).thenReturn("changed");assertThrows(ResponseStatusException.class,()->tickets.require(grant.ticket));}
    @Test void ticketsAreIndependentAndRestartDoesNotAcceptOldGrant(){var a=tickets.issue("private-session",input());var b=tickets.issue("private-session",input());assertNotEquals(a.ticket,b.ticket);assertThrows(ResponseStatusException.class,()->new NativeFileTickets(sessions,drive,versions,zip,clock).require(a.ticket));}
    @ParameterizedTest @ValueSource(strings={"", "preview", "DELETE", "save", "http://evil"}) void invalidActionsRejected(String action){var request=input();request.action=action;assertThrows(ResponseStatusException.class,()->tickets.issue("private-session",request));}
    @Test void duplicateAndCrossVersionBatchRejected(){var r=input();r.itemIds=List.of("file-1","file-1");assertThrows(ResponseStatusException.class,()->tickets.issue("private-session",r));r.itemIds=List.of("file-1","file-2");r.versionId="v1";assertThrows(ResponseStatusException.class,()->tickets.issue("private-session",r));}
    @Test void zipIncludesPreflightAndDetectsChanges(){var request=input();request.archive=true;when(zip.fingerprint(anyString(),any())).thenReturn("snapshot");var grant=tickets.issue("private-session",request);assertEquals("application/zip",grant.mimeType);when(zip.fingerprint(anyString(),any())).thenReturn("changed");assertThrows(ResponseStatusException.class,()->tickets.require(grant.ticket));}
    @Test void oldGrantNeverReadsNewAccountToken(){var grant=tickets.issue("private-session",input());tickets.require(grant.ticket);verify(sessions,never()).currentSession("new-account");}
    @Test void aHistoricalVersionIsFrozenIndependentlyOfTheCurrentFile(){
        var version=new com.x.cloudnext.server.drive.next.DriveItemVersion();version.id="v1";version.storageKey="history";version.sha256="digest";version.fileName="history.pdf";version.contentType="application/pdf";version.sizeBytes=45;
        when(versions.requireVersion(any(),eq("file-1"),eq("v1"))).thenReturn(version);
        var request=input();request.versionId="v1";var grant=tickets.issue("private-session",request);assertEquals("history.pdf",grant.fileName);
        assertSame(grant,tickets.require(grant.ticket));version.sha256="changed";assertThrows(ResponseStatusException.class,()->tickets.require(grant.ticket));
    }
    @Test void aTicketCannotBeReadInAnotherDriveNamespace(){
        var session=new AuthLoginResponse();session.setDrive(new com.x.cloudnext.server.namespace.DriveDescriptor("drive-a","entry-a","A"));when(sessions.currentSession("private-session")).thenReturn(session);
        var grant=tickets.issue("private-session",input());session.setDrive(new com.x.cloudnext.server.namespace.DriveDescriptor("drive-b","entry-b","B"));
        assertThrows(ResponseStatusException.class,()->tickets.require(grant.ticket));
    }
}
