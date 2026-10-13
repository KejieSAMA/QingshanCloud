package com.x.cloudnext.server.drive;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.cloudnext.server.drive.entity.DriveItem;

class CloudNextDriveFileOperationsTest extends AbstractDriveIntegrationTest {

    @Test
    void copyItems_recursesDeduplicatesSelectedChildrenAndKeepsSource() throws Exception {
        DriveItem sourceFolder = seedFolder(DEFAULT_OWNER_ID, "", "资料");
        DriveItem sourceFile = seedFile(
                DEFAULT_OWNER_ID,
                sourceFolder.getId(),
                "说明1.txt",
                "file",
                MediaType.TEXT_PLAIN_VALUE,
                "copy-content".getBytes());
        DriveItem targetFolder = seedFolder(DEFAULT_OWNER_ID, "", "目标");

        String copyJson = mockMvc.perform(post("/api/drive/items/copy")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + sourceFolder.getId() + "\",\"" + sourceFile.getId()
                        + "\"],\"targetScopeType\":\"PERSONAL\",\"targetParentId\":\"" + targetFolder.getId()
                        + "\",\"conflictStrategy\":\"keep_both\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(1))
                .andExpect(jsonPath("$.totalSucceeded").value(1))
                .andExpect(jsonPath("$.totalFailed").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode copyResult = objectMapper.readTree(copyJson);
        String copiedFolderId = copyResult.path("createdIdsBySource").path(sourceFolder.getId()).asText();
        DriveItem copiedFolder = reloadItem(copiedFolderId);
        org.junit.jupiter.api.Assertions.assertEquals("资料", copiedFolder.getName());
        org.junit.jupiter.api.Assertions.assertEquals(targetFolder.getId(), copiedFolder.getParentId());
        org.junit.jupiter.api.Assertions.assertNotEquals(sourceFolder.getId(), copiedFolder.getId());
        org.junit.jupiter.api.Assertions.assertFalse(reloadItem(sourceFolder.getId()).isDeleted());

        java.util.List<DriveItem> copiedChildren = driveItemRepository
                .findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(
                        DriveScopeType.PERSONAL, DEFAULT_OWNER_ID, copiedFolderId);
        org.junit.jupiter.api.Assertions.assertEquals(1, copiedChildren.size());
        DriveItem copiedFile = copiedChildren.get(0);
        org.junit.jupiter.api.Assertions.assertNotEquals(sourceFile.getId(), copiedFile.getId());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                storage.get(sourceFile.getStorageKey()), storage.get(copiedFile.getStorageKey()));

        mockMvc.perform(post("/api/drive/items/copy")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + sourceFolder.getId()
                        + "\"],\"targetScopeType\":\"PERSONAL\",\"targetParentId\":\"" + targetFolder.getId()
                        + "\",\"conflictStrategy\":\"keep_both\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1));

        java.util.List<DriveItem> visibleTargets = driveItemRepository
                .findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(
                        DriveScopeType.PERSONAL, DEFAULT_OWNER_ID, targetFolder.getId());
        org.junit.jupiter.api.Assertions.assertTrue(
                visibleTargets.stream().anyMatch(item -> "资料 (1)".equals(item.getName())));
    }

    @Test
    void createFolderAndUploadSupportConflictStrategies() throws Exception {
        String createdJson = mockMvc.perform(post("/api/drive/me/folders")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Workspace\",\"parentId\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Workspace"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        DriveItemResponse folder = objectMapper.readValue(createdJson, DriveItemResponse.class);

        String originalJson = mockMvc.perform(multipart("/api/drive/me/files")
                .file(uploadFile("readme.txt", MediaType.TEXT_PLAIN_VALUE, "one"))
                .header(TOKEN_HEADER, token)
                .param("parentId", folder.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("readme.txt"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        DriveItemResponse original = objectMapper.readValue(originalJson, DriveItemResponse.class);

        mockMvc.perform(multipart("/api/drive/me/files")
                .file(uploadFile("readme.txt", MediaType.TEXT_PLAIN_VALUE, "two"))
                .header(TOKEN_HEADER, token)
                .param("parentId", folder.getId())
                .param("conflictStrategy", "abort"))
                .andExpect(status().isConflict());

        mockMvc.perform(multipart("/api/drive/me/files")
                .file(uploadFile("readme.txt", MediaType.TEXT_PLAIN_VALUE, "two"))
                .header(TOKEN_HEADER, token)
                .param("parentId", folder.getId())
                .param("conflictStrategy", "keep_both"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("readme (1).txt"));

        mockMvc.perform(multipart("/api/drive/me/files")
                .file(uploadFile("readme.txt", MediaType.TEXT_PLAIN_VALUE, "updated"))
                .header(TOKEN_HEADER, token)
                .param("parentId", folder.getId())
                .param("conflictStrategy", "replace"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(original.getId()))
                .andExpect(jsonPath("$.name").value("readme.txt"));

        mockMvc.perform(get("/api/drive/me/items/query")
                .header(TOKEN_HEADER, token)
                .param("parentId", folder.getId())
                .param("sortBy", "name")
                .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void moveItems_supportAbortKeepBothReplaceAndDescendantGuard() throws Exception {
        DriveItem folderA = seedFolder(DEFAULT_OWNER_ID, "", "Folder-A");
        DriveItem folderB = seedFolder(DEFAULT_OWNER_ID, "", "Folder-B");
        DriveItem source = seedFile(DEFAULT_OWNER_ID, folderA.getId(), "report.txt", "file", MediaType.TEXT_PLAIN_VALUE, "A".getBytes());
        seedFile(DEFAULT_OWNER_ID, folderB.getId(), "report.txt", "file", MediaType.TEXT_PLAIN_VALUE, "B".getBytes());

        mockMvc.perform(post("/api/drive/items/move")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + source.getId() + "\"],\"targetParentId\":\"" + folderB.getId()
                        + "\",\"conflictStrategy\":\"abort\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(0))
                .andExpect(jsonPath("$.totalFailed").value(1));

        mockMvc.perform(post("/api/drive/items/move")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + source.getId() + "\"],\"targetParentId\":\"" + folderB.getId()
                        + "\",\"conflictStrategy\":\"keep_both\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1))
                .andExpect(jsonPath("$.totalFailed").value(0));

        DriveItem moved = reloadItem(source.getId());
        org.junit.jupiter.api.Assertions.assertEquals(folderB.getId(), moved.getParentId());
        org.junit.jupiter.api.Assertions.assertEquals("report (1).txt", moved.getName());

        DriveItem sourceReplace = seedFile(
                DEFAULT_OWNER_ID,
                folderA.getId(),
                "summary.txt",
                "file",
                MediaType.TEXT_PLAIN_VALUE,
                "source".getBytes());
        DriveItem targetReplace = seedFile(
                DEFAULT_OWNER_ID,
                folderB.getId(),
                "summary.txt",
                "file",
                MediaType.TEXT_PLAIN_VALUE,
                "target".getBytes());

        mockMvc.perform(post("/api/drive/items/move")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + sourceReplace.getId() + "\"],\"targetParentId\":\"" + folderB.getId()
                        + "\",\"conflictStrategy\":\"replace\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1));

        org.junit.jupiter.api.Assertions.assertFalse(driveItemRepository.findById(targetReplace.getId()).isPresent());
        org.junit.jupiter.api.Assertions.assertEquals(folderB.getId(), reloadItem(sourceReplace.getId()).getParentId());

        DriveItem parent = seedFolder(DEFAULT_OWNER_ID, "", "Parent");
        DriveItem child = seedFolder(DEFAULT_OWNER_ID, parent.getId(), "Child");

        mockMvc.perform(post("/api/drive/items/move")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + parent.getId() + "\"],\"targetParentId\":\"" + child.getId()
                        + "\",\"conflictStrategy\":\"abort\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(0))
                .andExpect(jsonPath("$.totalFailed").value(1));
    }

    @Test
    void renameItemAndBatchRenamePreserveExtensions() throws Exception {
        DriveItem document = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "draft.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "doc".getBytes());
        seedFile(
                DEFAULT_OWNER_ID,
                "",
                "taken.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "taken".getBytes());

        mockMvc.perform(patch("/api/drive/items/{id}/name", document.getId())
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"overview.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("overview.docx"));

        mockMvc.perform(patch("/api/drive/items/{id}/name", document.getId())
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"taken.docx\"}"))
                .andExpect(status().isConflict());

        DriveItem sheet = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "budget.xlsx",
                "excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "sheet".getBytes());
        DriveItem folder = seedFolder(DEFAULT_OWNER_ID, "", "assets");

        mockMvc.perform(post("/api/drive/items/batch-rename")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new DriveBatchRenameRequestBuilder()
                        .add(document.getId())
                        .add(sheet.getId())
                        .add(folder.getId())
                        .baseName("batch")
                        .startNumber(7)
                        .numberWidth(3)
                        .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(3))
                .andExpect(jsonPath("$.totalFailed").value(0));

        org.junit.jupiter.api.Assertions.assertEquals("batch-007.docx", reloadItem(document.getId()).getName());
        org.junit.jupiter.api.Assertions.assertEquals("batch-008.xlsx", reloadItem(sheet.getId()).getName());
        org.junit.jupiter.api.Assertions.assertEquals("batch-009", reloadItem(folder.getId()).getName());
    }

    private static class DriveBatchRenameRequestBuilder {
        private final DriveBatchRenameRequest request = new DriveBatchRenameRequest();

        DriveBatchRenameRequestBuilder add(String id) {
            request.getItemIds().add(id);
            return this;
        }

        DriveBatchRenameRequestBuilder baseName(String baseName) {
            request.setBaseName(baseName);
            return this;
        }

        DriveBatchRenameRequestBuilder startNumber(int startNumber) {
            request.setStartNumber(startNumber);
            return this;
        }

        DriveBatchRenameRequestBuilder numberWidth(int numberWidth) {
            request.setNumberWidth(numberWidth);
            return this;
        }

        DriveBatchRenameRequest build() {
            return request;
        }
    }
}
