package com.x.cloudnext.server.drive;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.x.cloudnext.server.drive.entity.DriveItem;

class CloudNextDriveTrashFlowTest extends AbstractDriveIntegrationTest {

    @Test
    void deleteAndTrashQueryWork() throws Exception {
        DriveItem file = seedFile(DEFAULT_OWNER_ID, "", "trash-me.txt", "file", MediaType.TEXT_PLAIN_VALUE, "trash".getBytes());

        mockMvc.perform(delete("/api/drive/items/{id}", file.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        mockMvc.perform(get("/api/drive/me/items/query")
                .header(TOKEN_HEADER, token)
                .param("parentId", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/drive/trash/items/query")
                .header(TOKEN_HEADER, token)
                .param("keyword", "trash")
                .param("sortBy", "deletedAt")
                .param("sortOrder", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(file.getId()));
    }

    @Test
    void trashQuery_supportsTimeRangeFiltering() throws Exception {
        DriveItem older = seedFile(DEFAULT_OWNER_ID, "", "older.txt", "file", MediaType.TEXT_PLAIN_VALUE, "older".getBytes());
        DriveItem newer = seedFile(DEFAULT_OWNER_ID, "", "newer.txt", "file", MediaType.TEXT_PLAIN_VALUE, "newer".getBytes());

        mockMvc.perform(delete("/api/drive/items/{id}", older.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/drive/items/{id}", newer.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk());

        DriveItem olderState = reloadItem(older.getId());
        olderState.setDeletedAt("2026-05-01T08:00:00Z");
        olderState.setUpdatedAt(olderState.getDeletedAt());
        syncItem(olderState);

        DriveItem newerState = reloadItem(newer.getId());
        newerState.setDeletedAt("2026-05-12T18:30:00Z");
        newerState.setUpdatedAt(newerState.getDeletedAt());
        syncItem(newerState);

        mockMvc.perform(get("/api/drive/trash/items/query")
                .header(TOKEN_HEADER, token)
                .param("timeFrom", "2026-05-10T00:00:00Z")
                .param("timeTo", "2026-05-13T00:00:00Z")
                .param("sortBy", "deletedAt")
                .param("sortOrder", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(newer.getId()));
    }

    @Test
    void restoreRequiresParentSelectionAndParentRestoreRecoversChildren() throws Exception {
        DriveItem folder = seedFolder(DEFAULT_OWNER_ID, "", "RestoreRoot");
        DriveItem child = seedFile(DEFAULT_OWNER_ID, folder.getId(), "child.txt", "file", MediaType.TEXT_PLAIN_VALUE, "child".getBytes());

        mockMvc.perform(delete("/api/drive/items/{id}", folder.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/drive/trash/items/restore")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + child.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(0))
                .andExpect(jsonPath("$.totalFailed").value(1));

        org.junit.jupiter.api.Assertions.assertTrue(reloadItem(child.getId()).isDeleted());

        mockMvc.perform(post("/api/drive/trash/items/restore")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + folder.getId() + "\",\"" + child.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1))
                .andExpect(jsonPath("$.totalFailed").value(0));

        org.junit.jupiter.api.Assertions.assertFalse(reloadItem(folder.getId()).isDeleted());
        org.junit.jupiter.api.Assertions.assertFalse(reloadItem(child.getId()).isDeleted());
    }

    @Test
    void purgeClearAndScheduledCleanupRemoveOnlyExpectedItems() throws Exception {
        DriveItem purgeFolder = seedFolder(DEFAULT_OWNER_ID, "", "PurgeRoot");
        DriveItem purgeChild = seedFile(DEFAULT_OWNER_ID, purgeFolder.getId(), "purge.txt", "file", MediaType.TEXT_PLAIN_VALUE, "purge".getBytes());
        mockMvc.perform(delete("/api/drive/items/{id}", purgeFolder.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/drive/trash/items/purge")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + purgeFolder.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1));

        org.junit.jupiter.api.Assertions.assertFalse(driveItemRepository.findById(purgeFolder.getId()).isPresent());
        org.junit.jupiter.api.Assertions.assertFalse(driveItemRepository.findById(purgeChild.getId()).isPresent());
        org.junit.jupiter.api.Assertions.assertFalse(storage.containsKey(purgeChild.getStorageKey()));

        DriveItem mine = seedFile(DEFAULT_OWNER_ID, "", "mine.txt", "file", MediaType.TEXT_PLAIN_VALUE, "mine".getBytes());
        DriveItem other = seedFile("person-b", "", "other.txt", "file", MediaType.TEXT_PLAIN_VALUE, "other".getBytes());
        mockMvc.perform(delete("/api/drive/items/{id}", mine.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk());
        String personBToken = createSession("person-b", "User B");
        mockMvc.perform(delete("/api/drive/items/{id}", other.getId())
                .header(TOKEN_HEADER, personBToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/drive/trash/clear")
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1));

        org.junit.jupiter.api.Assertions.assertFalse(driveItemRepository.findById(mine.getId()).isPresent());
        org.junit.jupiter.api.Assertions.assertTrue(reloadItem(other.getId()).isDeleted());

        DriveItem expired = seedFile(DEFAULT_OWNER_ID, "", "expired.txt", "file", MediaType.TEXT_PLAIN_VALUE, "expired".getBytes());
        DriveItem recent = seedFile(DEFAULT_OWNER_ID, "", "recent.txt", "file", MediaType.TEXT_PLAIN_VALUE, "recent".getBytes());
        mockMvc.perform(delete("/api/drive/items/{id}", expired.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/drive/items/{id}", recent.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk());

        DriveItem expiredState = reloadItem(expired.getId());
        expiredState.setDeletedAt(Instant.now().minusSeconds(31L * 24L * 3600L).toString());
        expiredState.setUpdatedAt(expiredState.getDeletedAt());
        syncItem(expiredState);

        DriveItem recentState = reloadItem(recent.getId());
        recentState.setDeletedAt(Instant.now().minusSeconds(5L * 24L * 3600L).toString());
        recentState.setUpdatedAt(recentState.getDeletedAt());
        syncItem(recentState);

        driveTrashCleanupService.cleanupExpiredTrash();

        org.junit.jupiter.api.Assertions.assertFalse(driveItemRepository.findById(expired.getId()).isPresent());
        org.junit.jupiter.api.Assertions.assertTrue(driveItemRepository.findById(recent.getId()).isPresent());
        org.junit.jupiter.api.Assertions.assertFalse(storage.containsKey(expired.getStorageKey()));
    }
}
