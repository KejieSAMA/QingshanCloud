package com.x.cloudnext.server.preview;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.x.cloudnext.server.drive.DriveService;
import com.x.cloudnext.server.web.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PreviewHttpContractTest {
    @TempDir Path root;
    @Test void pdfRangeUses206AndDoesNotExposeAnUnauthenticatedStaticPath()throws Exception {
        DriveFilePreviewService service=mock(DriveFilePreviewService.class);Path p=root.resolve("large.pdf");Files.writeString(p,"0123456789");
        when(service.content("ticket")).thenReturn(new DriveService.DriveDownloadPayload("测试.pdf","application/pdf",new FileSystemResource(p)));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new CloudNextPreviewController(service)).build();
        mvc.perform(get("/api/previews/content").header("X-CloudNext-Preview","ticket").header("Range","bytes=2-5"))
            .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 2-5/10"))
            .andExpect(header().string("Cache-Control","private, no-store")).andExpect(content().string("2345"));
        mvc.perform(get("/api/previews/content")).andExpect(status().isBadRequest());
    }
    @Test void staleEditorEndpointsAreGoneAndNeverWriteBack()throws Exception {
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new CloudNextOnlyofficeController()).setControllerAdvice(new CloudNextExceptionHandler("100MB")).build();
        for(String path:new String[]{"edit-config","view-config","force-save","callback","content","save-status","health"})
            mvc.perform(post("/api/onlyoffice/"+path)).andExpect(status().isGone()).andExpect(jsonPath("$.message").value("在线编辑服务已停用，请重新打开文件预览。"));
    }
}
