package com.x.cloudnext.server.web;

import com.x.cloudnext.server.drive.DrivePreviewResponse;
import com.x.cloudnext.server.preview.DriveFilePreviewService;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/api/previews")
public class CloudNextPreviewController {
    private final DriveFilePreviewService previews;
    public CloudNextPreviewController(DriveFilePreviewService previews){this.previews=previews;}
    @GetMapping public DrivePreviewResponse status(@RequestHeader("X-CloudNext-Preview")String ticket){return previews.inspect(ticket,false,false);}
    @PostMapping public DrivePreviewResponse generate(@RequestHeader("X-CloudNext-Preview")String ticket,@RequestParam(defaultValue="false")boolean retry){return previews.inspect(ticket,true,retry);}
    @GetMapping("/content") public ResponseEntity<Resource> content(@RequestHeader("X-CloudNext-Preview")String ticket){
        var p=previews.content(ticket);
        // Spring MVC serves Resource ranges as 206; it never buffers the complete file.
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.getContentType()))
            .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
            .header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","sandbox; default-src 'none'")
            .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename*=UTF-8''"+UriUtils.encode(p.getFileName(),StandardCharsets.UTF_8))
            .body(p.getResource());
    }
}
