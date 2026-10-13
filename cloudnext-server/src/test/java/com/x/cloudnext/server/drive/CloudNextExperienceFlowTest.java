package com.x.cloudnext.server.drive;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.next.*;

class CloudNextExperienceFlowTest extends AbstractDriveIntegrationTest {
 @Autowired DriveItemVersionRepository versions;
 @Autowired DriveVersionService versionService;
 @Autowired PlatformTransactionManager transactions;

 @Test void preferencesAndFavoritesArePrincipalScoped() throws Exception {
  String other=createSession("person-b","User B");
  mockMvc.perform(put("/api/drive/preferences/ui").header(TOKEN_HEADER,token).contentType(MediaType.APPLICATION_JSON)
   .content("{\"primaryColor\":\"green\",\"pageSize\":100}")).andExpect(status().isOk());
  mockMvc.perform(get("/api/drive/preferences/ui").header(TOKEN_HEADER,token)).andExpect(jsonPath("$.primaryColor").value("green"));
  mockMvc.perform(get("/api/drive/preferences/ui").header(TOKEN_HEADER,other)).andExpect(jsonPath("$.primaryColor").value("blue")).andExpect(jsonPath("$.pageSize").value(50));
  mockMvc.perform(put("/api/drive/preferences/ui").header(TOKEN_HEADER,token).contentType(MediaType.APPLICATION_JSON).content("{\"pageSize\":1000}")).andExpect(status().isBadRequest());
  DriveItem file=seedFile(DEFAULT_OWNER_ID,"","favorite.txt","file","text/plain","test".getBytes(StandardCharsets.UTF_8));
  mockMvc.perform(put("/api/drive/me/favorites/"+file.getId()).header(TOKEN_HEADER,token)).andExpect(status().isOk());
  mockMvc.perform(get("/api/drive/me/favorites").header(TOKEN_HEADER,token)).andExpect(jsonPath("$.total").value(1));
  mockMvc.perform(get("/api/drive/me/favorites").header(TOKEN_HEADER,other)).andExpect(jsonPath("$.total").value(0));
  mockMvc.perform(put("/api/drive/me/favorites/"+file.getId()).header(TOKEN_HEADER,other)).andExpect(status().isNotFound());
  mockMvc.perform(delete("/api/drive/items/"+file.getId()).header(TOKEN_HEADER,token)).andExpect(status().isOk());
  mockMvc.perform(get("/api/drive/me/favorites").header(TOKEN_HEADER,token)).andExpect(jsonPath("$.total").value(0));
 }

 @Test void replacementPreservesImmutableHistoryAndPurgeRemovesBoth() throws Exception {
  DriveItem initial=seedFile(DEFAULT_OWNER_ID,"","history.txt","file","text/plain","before".getBytes(StandardCharsets.UTF_8));
  mockMvc.perform(multipart("/api/drive/me/files").file(uploadFile("history.txt","text/plain","after"))
   .param("conflictStrategy","replace").header(TOKEN_HEADER,token)).andExpect(status().isCreated());
  List<DriveItemVersion> list=versions.findByItemIdOrderByVersionNumberDesc(initial.getId());
  assertEquals(2,list.size());
  assertNotEquals(list.get(0).storageKey,list.get(1).storageKey);
  assertArrayEquals("before".getBytes(StandardCharsets.UTF_8),storage.get(list.get(1).storageKey));
  mockMvc.perform(get("/api/drive/items/"+initial.getId()+"/versions/"+list.get(1).id+"/download").header(TOKEN_HEADER,token))
   .andExpect(status().isOk()).andExpect(content().string("before"));
  mockMvc.perform(get("/api/drive/items/"+initial.getId()+"/versions").header(TOKEN_HEADER,createSession("person-b","User B")))
   .andExpect(status().isNotFound());
  mockMvc.perform(multipart("/api/drive/me/files").file(uploadFile("history.txt","text/plain","after"))
   .param("conflictStrategy","replace").header(TOKEN_HEADER,token)).andExpect(status().isCreated());
  assertEquals(2,versions.findByItemIdOrderByVersionNumberDesc(initial.getId()).size());
  mockMvc.perform(get("/api/drive/me/storage-usage").header(TOKEN_HEADER,token))
   .andExpect(jsonPath("$.usedBytes").value(11)).andExpect(jsonPath("$.versionBytes").value(6))
   .andExpect(jsonPath("$.enforced").value(false)).andExpect(jsonPath("$.referenceBytes").value(107374182400L));
  mockMvc.perform(delete("/api/drive/items/"+initial.getId()).header(TOKEN_HEADER,token)).andExpect(status().isOk());
  mockMvc.perform(get("/api/drive/items/"+initial.getId()+"/versions").header(TOKEN_HEADER,token)).andExpect(status().isNotFound());
  mockMvc.perform(post("/api/drive/trash/items/purge").header(TOKEN_HEADER,token).contentType(MediaType.APPLICATION_JSON)
   .content("{\"itemIds\":[\""+initial.getId()+"\"]}")).andExpect(status().isOk()).andExpect(jsonPath("$.totalSucceeded").value(1));
  assertTrue(versions.findByItemIdOrderByVersionNumberDesc(initial.getId()).isEmpty());
  for(DriveItemVersion v:list)assertFalse(storage.containsKey(v.storageKey));
 }

 @Test void storageFailureKeepsCurrentBytesAndMetadata() throws Exception {
  DriveItem initial=seedFile(DEFAULT_OWNER_ID,"","failure.txt","file","text/plain","before".getBytes(StandardCharsets.UTF_8));
  org.mockito.Mockito.when(driveStorageService.store(anyString(),any(MultipartFile.class))).thenThrow(new IllegalStateException("test storage failure"));
  try {
   mockMvc.perform(multipart("/api/drive/me/files").file(uploadFile("failure.txt","text/plain","after"))
    .param("conflictStrategy","replace").header(TOKEN_HEADER,token));
  } catch(Exception expected) { /* An unhandled storage failure may propagate through MockMvc. */ }
  assertEquals(initial.getStorageKey(),reloadItem(initial.getId()).getStorageKey());
  assertArrayEquals("before".getBytes(StandardCharsets.UTF_8),storage.get(initial.getStorageKey()));
  assertTrue(versions.findByItemIdOrderByVersionNumberDesc(initial.getId()).isEmpty());
 }

 @Test void recentHistoryIsPerPersonAndDoesNotImportGlobalTimestamp() throws Exception {
  DriveItem initial=seedFile(DEFAULT_OWNER_ID,"","recent.txt","file","text/plain","test".getBytes(StandardCharsets.UTF_8));
  mockMvc.perform(get("/api/drive/me/recent-opened/query").header(TOKEN_HEADER,token)).andExpect(jsonPath("$.total").value(0));
  mockMvc.perform(post("/api/drive/me/recent-opened/"+initial.getId()).header(TOKEN_HEADER,token)).andExpect(status().isOk());
  mockMvc.perform(get("/api/drive/me/recent-opened/query").header(TOKEN_HEADER,token)).andExpect(jsonPath("$.items[0].id").value(initial.getId()));
  mockMvc.perform(get("/api/drive/me/recent-opened/query").header(TOKEN_HEADER,createSession("person-b","User B"))).andExpect(jsonPath("$.total").value(0));
 }

 @Test void durableReceiptDoesNotReplayOldContentAfterNewerSave() {
  DriveItem file=seedFile(DEFAULT_OWNER_ID,"","callback.docx","word","application/octet-stream","baseline".getBytes(StandardCharsets.UTF_8));
  commitCallback(file.getId(),"first","callback-1");
  commitCallback(file.getId(),"second","callback-2");
  commitCallback(file.getId(),"first","callback-1");
  DriveItem current=reloadItem(file.getId());
  assertArrayEquals("second".getBytes(StandardCharsets.UTF_8),storage.get(current.getStorageKey()));
  assertEquals(3,versions.findByItemIdOrderByVersionNumberDesc(file.getId()).size());
 }
 private void commitCallback(String id,String bytes,String key) {
  new TransactionTemplate(transactions).execute(status -> {
   DriveItem item=driveItemRepository.findById(id).orElseThrow();
   DriveStoredFile saved=versionService.store(item,bytes.getBytes(StandardCharsets.UTF_8),DEFAULT_OWNER_ID,DEFAULT_OWNER_NAME,key);
   item.setStorageKey(saved.getStorageKey());item.setSizeBytes(saved.getSizeBytes());driveItemRepository.save(item);return null;
  });
 }
}
