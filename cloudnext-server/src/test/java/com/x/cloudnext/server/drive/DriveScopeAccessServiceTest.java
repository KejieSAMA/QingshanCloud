package com.x.cloudnext.server.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.auth.AuthSessionService;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.workspace.repository.WorkspaceMemberRepository;
import com.x.cloudnext.server.workspace.repository.WorkspaceRepository;

/**
 * Unit-test coverage for the 4 role × 5 action permission matrix exposed by
 * {@link DriveScopeAccessService}. Plus negative cases:
 * <ul>
 *   <li>non-member workspace access</li>
 *   <li>scope inconsistency (WORKSPACE item with empty/mismatched scopeId)</li>
 *   <li>null safety in {@code canActOn}</li>
 *   <li>{@code requireItem} error semantics (401 / 404 / 403)</li>
 * </ul>
 *
 * <p>Because the WorkspaceMember repository is introduced later (task 1.1), the
 * production {@code resolveWorkspaceRole} returns {@code null}. To exercise the matrix
 * for every role we subclass the SUT and let each test pin the resolved role via
 * {@link TestableDriveScopeAccessService#stubbedRole}.
 */
class DriveScopeAccessServiceTest {

    private static final String OWNER_ID = "person-a";
    private static final String OTHER_ID = "person-b";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String ITEM_ID = "item-1";

    private DriveItemRepository driveItemRepository;
    private AuthSessionService authSessionService;
    private DriveShareService driveShareService;
    private WorkspaceMemberRepository workspaceMemberRepository;
    private WorkspaceRepository workspaceRepository;
    private TestableDriveScopeAccessService access;

    @BeforeEach
    void setUp() {
        driveItemRepository = mock(DriveItemRepository.class);
        authSessionService = mock(AuthSessionService.class);
        driveShareService = mock(DriveShareService.class);
        workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        workspaceRepository = mock(WorkspaceRepository.class);
        access = new TestableDriveScopeAccessService(
                driveItemRepository,
                authSessionService,
                driveShareService,
                workspaceMemberRepository,
                workspaceRepository);
    }

    // ---------------------------------------------------------------
    // PERSONAL scope
    // ---------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(DriveScopeAction.class)
    void personalOwnerCanPerformAllActions(DriveScopeAction action) {
        AuthLoginResponse session = session(OWNER_ID);
        DriveItem item = personalItem(OWNER_ID);
        when(driveItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        assertTrue(access.canActOn(session, item, action),
                "PERSONAL owner should be allowed to perform " + action);

        DriveItem resolved = access.requireItem(session, ITEM_ID, action);
        assertSame(item, resolved);
    }

    @Test
    void personalNonOwnerReadAllowedWhenShareGrantsAccess() {
        AuthLoginResponse session = session(OTHER_ID);
        DriveItem item = personalItem(OWNER_ID);
        DriveResolvedShareAccess shareAccess = mock(DriveResolvedShareAccess.class);
        when(driveShareService.resolveAccess(session, item)).thenReturn(Optional.of(shareAccess));

        assertTrue(access.canActOn(session, item, DriveScopeAction.READ));
    }

    @ParameterizedTest
    @EnumSource(value = DriveScopeAction.class,
            names = { "WRITE", "DELETE", "MANAGE_SHARE", "MANAGE_MEMBER" })
    void personalNonOwnerCannotPerformWriteEvenWithShare(DriveScopeAction action) {
        AuthLoginResponse session = session(OTHER_ID);
        DriveItem item = personalItem(OWNER_ID);
        DriveResolvedShareAccess shareAccess = mock(DriveResolvedShareAccess.class);
        when(driveShareService.resolveAccess(session, item)).thenReturn(Optional.of(shareAccess));

        assertFalse(access.canActOn(session, item, action));
    }

    @Test
    void personalNonOwnerWithoutShareIsDenied() {
        AuthLoginResponse session = session(OTHER_ID);
        DriveItem item = personalItem(OWNER_ID);
        when(driveShareService.resolveAccess(session, item)).thenReturn(Optional.empty());
        when(driveItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        assertFalse(access.canActOn(session, item, DriveScopeAction.READ));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requireItem(session, ITEM_ID, DriveScopeAction.READ));
        // Task 0.5 preserves the legacy DriveAccessService.requireOwnedItem contract:
        // PERSONAL-scope denials surface as 404 "item not found" so existing personal-flow
        // tests / clients keep working unchanged after the call sites were swapped over.
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertContainsReason(ex, "item not found");
    }

    @Test
    void personalShareLookupErrorIsTreatedAsDenial() {
        AuthLoginResponse session = session(OTHER_ID);
        DriveItem item = personalItem(OWNER_ID);
        when(driveShareService.resolveAccess(session, item))
                .thenThrow(new RuntimeException("share lookup failed"));

        // The gateway is a predicate; downstream errors must not bubble up as auth decisions.
        assertFalse(access.canActOn(session, item, DriveScopeAction.READ));
    }

    // ---------------------------------------------------------------
    // WORKSPACE scope — non-member
    // ---------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(DriveScopeAction.class)
    void workspaceNonMemberIsDeniedForAllActions(DriveScopeAction action) {
        AuthLoginResponse session = session(OTHER_ID);
        DriveItem item = workspaceItem(WORKSPACE_ID);
        access.stubbedRole = null;
        when(driveItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        assertFalse(access.canActOn(session, item, action));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requireItem(session, ITEM_ID, action));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertContainsReason(ex, "workspace access denied");
    }

    // ---------------------------------------------------------------
    // WORKSPACE scope — full role × action matrix
    // ---------------------------------------------------------------

    static Stream<Arguments> workspaceMatrix() {
        return Stream.of(
                // VIEWER: only READ
                Arguments.of(WorkspaceRole.VIEWER, DriveScopeAction.READ, true),
                Arguments.of(WorkspaceRole.VIEWER, DriveScopeAction.WRITE, false),
                Arguments.of(WorkspaceRole.VIEWER, DriveScopeAction.DELETE, false),
                Arguments.of(WorkspaceRole.VIEWER, DriveScopeAction.MANAGE_SHARE, false),
                Arguments.of(WorkspaceRole.VIEWER, DriveScopeAction.MANAGE_MEMBER, false),
                // MEMBER: all but MANAGE_MEMBER
                Arguments.of(WorkspaceRole.MEMBER, DriveScopeAction.READ, true),
                Arguments.of(WorkspaceRole.MEMBER, DriveScopeAction.WRITE, true),
                Arguments.of(WorkspaceRole.MEMBER, DriveScopeAction.DELETE, true),
                Arguments.of(WorkspaceRole.MEMBER, DriveScopeAction.MANAGE_SHARE, true),
                Arguments.of(WorkspaceRole.MEMBER, DriveScopeAction.MANAGE_MEMBER, false),
                // ADMIN: full
                Arguments.of(WorkspaceRole.ADMIN, DriveScopeAction.READ, true),
                Arguments.of(WorkspaceRole.ADMIN, DriveScopeAction.WRITE, true),
                Arguments.of(WorkspaceRole.ADMIN, DriveScopeAction.DELETE, true),
                Arguments.of(WorkspaceRole.ADMIN, DriveScopeAction.MANAGE_SHARE, true),
                Arguments.of(WorkspaceRole.ADMIN, DriveScopeAction.MANAGE_MEMBER, true),
                // OWNER: full
                Arguments.of(WorkspaceRole.OWNER, DriveScopeAction.READ, true),
                Arguments.of(WorkspaceRole.OWNER, DriveScopeAction.WRITE, true),
                Arguments.of(WorkspaceRole.OWNER, DriveScopeAction.DELETE, true),
                Arguments.of(WorkspaceRole.OWNER, DriveScopeAction.MANAGE_SHARE, true),
                Arguments.of(WorkspaceRole.OWNER, DriveScopeAction.MANAGE_MEMBER, true));
    }

    @ParameterizedTest(name = "[{index}] role={0} action={1} -> {2}")
    @MethodSource("workspaceMatrix")
    void workspaceMatrixHonoursRoleActionTable(
            WorkspaceRole role, DriveScopeAction action, boolean expected) {
        AuthLoginResponse session = session(OTHER_ID);
        DriveItem item = workspaceItem(WORKSPACE_ID);
        access.stubbedRole = role;
        when(driveItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        assertEquals(expected, access.canActOn(session, item, action),
                "role=" + role + " action=" + action);

        if (expected) {
            assertSame(item, access.requireItem(session, ITEM_ID, action));
        } else {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> access.requireItem(session, ITEM_ID, action));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
            // VIEWER attempting non-READ surfaces the more specific "workspace write denied".
            if (role == WorkspaceRole.VIEWER && action != DriveScopeAction.READ) {
                assertContainsReason(ex, "workspace write denied");
            } else {
                assertContainsReason(ex, "workspace access denied");
            }
        }
    }

    // ---------------------------------------------------------------
    // Scope inconsistency: WORKSPACE item must not silently fall back to PERSONAL owner check
    // ---------------------------------------------------------------

    @Test
    void workspaceItemWithMismatchedScopeIdIsDenied() {
        AuthLoginResponse session = session(OWNER_ID); // would be owner under PERSONAL semantics
        DriveItem item = workspaceItem(""); // empty / mismatched workspaceId
        item.setOwnerPersonOaId(OWNER_ID);
        // resolveWorkspaceRole(session, "") returns null in the testable subclass.
        access.stubbedRole = null;
        when(driveItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        assertFalse(access.canActOn(session, item, DriveScopeAction.READ),
                "WORKSPACE item must not fall back to PERSONAL owner check");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requireItem(session, ITEM_ID, DriveScopeAction.READ));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertContainsReason(ex, "workspace access denied");
    }

    // ---------------------------------------------------------------
    // Null safety + requireItem error semantics
    // ---------------------------------------------------------------

    @Test
    void canActOnReturnsFalseForNullInputs() {
        DriveItem item = personalItem(OWNER_ID);
        AuthLoginResponse session = session(OWNER_ID);

        assertFalse(access.canActOn(null, item, DriveScopeAction.READ));
        assertFalse(access.canActOn(session, null, DriveScopeAction.READ));
        assertFalse(access.canActOn(session, item, null));
    }

    @Test
    void requireItemThrows401WhenSessionIsNull() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requireItem(null, ITEM_ID, DriveScopeAction.READ));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void requireItemThrows400WhenActionIsNull() {
        AuthLoginResponse session = session(OWNER_ID);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requireItem(session, ITEM_ID, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void requireItemThrows404WhenItemDoesNotExist() {
        AuthLoginResponse session = session(OWNER_ID);
        when(driveItemRepository.findById("missing")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requireItem(session, "missing", DriveScopeAction.READ));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertContainsReason(ex, "item not found");
    }

    @Test
    void requireSessionThrows401WhenSessionMissing() {
        when(authSessionService.currentSession("bad-token")).thenReturn(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requireSession("bad-token"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void requireSessionReturnsSessionWhenPresent() {
        AuthLoginResponse session = session(OWNER_ID);
        when(authSessionService.currentSession("good-token")).thenReturn(session);
        AuthLoginResponse resolved = access.requireSession("good-token");
        assertNotNull(resolved);
        assertSame(session, resolved);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static DriveItem personalItem(String ownerId) {
        DriveItem item = new DriveItem();
        item.setId(ITEM_ID);
        item.setName("file.txt");
        item.setItemType(DriveItemType.FILE);
        item.setOwnerPersonOaId(ownerId);
        item.setOwnerName(ownerId);
        item.setScopeType(DriveScopeType.PERSONAL);
        item.setScopeId(ownerId);
        return item;
    }

    private static DriveItem workspaceItem(String workspaceId) {
        DriveItem item = new DriveItem();
        item.setId(ITEM_ID);
        item.setName("doc.txt");
        item.setItemType(DriveItemType.FILE);
        item.setOwnerPersonOaId("someone-else");
        item.setOwnerName("Someone Else");
        item.setScopeType(DriveScopeType.WORKSPACE);
        item.setScopeId(workspaceId);
        return item;
    }

    private static AuthLoginResponse session(String personOaId) {
        AuthLoginResponse session = new AuthLoginResponse();
        session.setAuthenticated(true);
        session.setPersonOaId(personOaId);
        session.setPersonName(personOaId);
        session.setToken("token-" + personOaId);
        return session;
    }

    private static void assertContainsReason(ResponseStatusException ex, String fragment) {
        String reason = ex.getReason();
        assertNotNull(reason, "ResponseStatusException reason should not be null");
        assertTrue(reason.contains(fragment),
                "expected reason to contain '" + fragment + "' but was '" + reason + "'");
    }

    /**
     * Subclass that lets each test fix the resolved {@link WorkspaceRole} without
     * depending on the WorkspaceMember repository (introduced in task 1.1).
     */
    private static final class TestableDriveScopeAccessService extends DriveScopeAccessService {
        WorkspaceRole stubbedRole;

        TestableDriveScopeAccessService(
                DriveItemRepository driveItemRepository,
                AuthSessionService authSessionService,
                DriveShareService driveShareService,
                WorkspaceMemberRepository workspaceMemberRepository,
                WorkspaceRepository workspaceRepository) {
            super(
                    driveItemRepository,
                    authSessionService,
                    driveShareService,
                    workspaceMemberRepository,
                    workspaceRepository);
        }

        @Override
        public WorkspaceRole resolveWorkspaceRole(AuthLoginResponse session, String workspaceId) {
            return stubbedRole;
        }
    }

}
