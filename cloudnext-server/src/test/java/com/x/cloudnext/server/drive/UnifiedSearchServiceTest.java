package com.x.cloudnext.server.drive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.entity.*;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.workspace.entity.*;
import com.x.cloudnext.server.workspace.repository.*;

class UnifiedSearchServiceTest {
    private final DriveItemRepository files = mock(DriveItemRepository.class);
    private final WorkspaceRepository spaces = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    private final DriveShareService shares = mock(DriveShareService.class);
    private final UnifiedSearchService service = new UnifiedSearchService(files, spaces, members, shares);
    private final AuthLoginResponse session = new AuthLoginResponse();
    UnifiedSearchServiceTest() {
        session.setPersonOaId("me");
        when(files.findByScopeTypeAndScopeIdAndDeletedFalse(any(), anyString())).thenReturn(Collections.emptyList());
        when(shares.loadMatchedShares(session)).thenReturn(Collections.emptyList());
        when(members.findByPersonOaId("me")).thenReturn(Collections.emptyList());
        when(spaces.findAllById(any())).thenReturn(Collections.emptyList());
    }
    private DriveItem file(String id, String name, String parent) {
        DriveItem i = new DriveItem(); i.setId(id); i.setName(name); i.setParentId(parent);
        i.setScopeType(DriveScopeType.PERSONAL); i.setScopeId("me"); i.setOwnerPersonOaId("me");
        i.setOwnerName("用户"); i.setItemType(DriveItemType.FILE); i.setUpdatedAt("2026-09-09T00:00:00Z"); return i;
    }
    private UnifiedSearchRequest query(String name) { UnifiedSearchRequest q = new UnifiedSearchRequest(); q.setKeyword(name); return q; }
    @Test void nameOnlyAndNaturalOrderAndPagination() {
        DriveItem a=file("a","10-合同.PDF",""), b=file("b","2-合同.PDF",""), c=file("c","说明.txt",""); c.setOwnerName("合同");
        when(files.findByScopeTypeAndScopeIdAndDeletedFalse(DriveScopeType.PERSONAL,"me")).thenReturn(Arrays.asList(a,b,c));
        UnifiedSearchRequest q=query("合同 pdf"); q.setSortBy("name"); q.setSortOrder("asc"); q.setSize(1);
        UnifiedSearchResponse r=service.search(session,q); assertEquals(2,r.total); assertEquals("b",r.items.get(0).getId());
        q.setPage(2); assertEquals("a",service.search(session,q).items.get(0).getId());
    }
    @Test void deletedMissingAndCyclicAncestorsAreExcluded() {
        DriveItem a=file("a","合同","missing"), b=file("b","合同","c"), c=file("c","合同","b");
        b.setItemType(DriveItemType.FOLDER); c.setItemType(DriveItemType.FOLDER);
        when(files.findByScopeTypeAndScopeIdAndDeletedFalse(DriveScopeType.PERSONAL,"me")).thenReturn(Arrays.asList(a,b,c));
        assertEquals(0,service.search(session,query("合同")).total);
    }
    @Test void sharedDescendantsHaveSafePathsAndEffectiveDownload() {
        DriveItem hidden=file("hidden","私人父目录",""); hidden.setItemType(DriveItemType.FOLDER);
        DriveItem root=file("root","公开目录","hidden"); root.setItemType(DriveItemType.FOLDER);
        DriveItem child=file("child","合同.pdf","root");
        for(DriveItem i:Arrays.asList(hidden,root,child)){i.setScopeId("other");i.setOwnerPersonOaId("other");}
        DriveShare s=new DriveShare(); s.setId("share");s.setItemId("root");s.setPermission(DriveSharePermission.READ);
        when(shares.loadMatchedShares(session)).thenReturn(Collections.singletonList(s));
        when(files.findAllById(any())).thenReturn(Collections.singletonList(root));
        when(files.findByScopeTypeAndScopeIdAndDeletedFalse(DriveScopeType.PERSONAL,"other")).thenReturn(Arrays.asList(hidden,root,child));
        UnifiedSearchResponse r=service.search(session,query("合同")); assertEquals(1,r.total);
        assertEquals("share",r.items.get(0).shareId); assertFalse(r.items.get(0).displayPath.contains("私人"));
        assertTrue(r.items.get(0).allowPreview); assertFalse(r.items.get(0).allowDownload);
        s.setPermission(DriveSharePermission.DOWNLOAD); assertTrue(service.search(session,query("合同")).items.get(0).allowDownload);
        s.setRevoked(true); assertEquals(0,service.search(session,query("合同")).total);
    }
    @Test void workspaceRolesAndArchivedSpacesRespectVisibility() {
        Workspace w=new Workspace();w.setId("ws");w.setName("合同空间");w.setRootItemId("root");
        WorkspaceMember m=new WorkspaceMember();m.setWorkspaceId("ws");m.setRole(WorkspaceRole.VIEWER);
        DriveItem root=file("root","","");root.setItemType(DriveItemType.FOLDER);
        DriveItem child=file("child","合同.pdf","root");
        for(DriveItem i:Arrays.asList(root,child)){i.setScopeType(DriveScopeType.WORKSPACE);i.setScopeId("ws");}
        when(members.findByPersonOaId("me")).thenReturn(Collections.singletonList(m));
        when(spaces.findAllById(any())).thenReturn(Collections.singletonList(w));
        when(files.findByScopeTypeAndScopeIdAndDeletedFalse(DriveScopeType.WORKSPACE,"ws")).thenReturn(Arrays.asList(root,child));
        for(WorkspaceRole role:WorkspaceRole.values()) {
            m.setRole(role); UnifiedSearchResponse r=service.search(session,query("合同")); assertEquals(2,r.total);
            assertEquals(role != WorkspaceRole.VIEWER,r.items.stream().filter(i->i.getId().equals("child")).findFirst().get().allowDownload);
        }
        w.setStatus(Workspace.STATUS_ARCHIVED);assertEquals(0,service.search(session,query("合同")).total);
    }
    @Test void invalidExpiryFailsClosedAndRelevanceUsesName() {
        DriveShare s=new DriveShare();s.setPermission(DriveSharePermission.READ);s.setExpiresAt("invalid");
        assertFalse(UnifiedSearchService.activeShare(s,Instant.now()));
        assertEquals(0,UnifiedSearchService.rank("合同","合同"));assertEquals(1,UnifiedSearchService.rank("合同.pdf","合同"));
        assertEquals(2,UnifiedSearchService.rank("2026合同.pdf","合同"));
        assertTrue(UnifiedSearchService.naturalCompare("2-合同","10-合同")<0);
    }
}
