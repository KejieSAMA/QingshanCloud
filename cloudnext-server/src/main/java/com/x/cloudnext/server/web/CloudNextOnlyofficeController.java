package com.x.cloudnext.server.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Old clients fail explicitly; no editor script, content export, callback writes or remote probes. */
@RestController
@RequestMapping("/api/onlyoffice")
public class CloudNextOnlyofficeController {
    @RequestMapping(value={"/health","/view-config","/edit-config","/force-save","/save-status","/content","/callback"})
    public void retired() { throw new ResponseStatusException(HttpStatus.GONE,"在线编辑服务已停用，请重新打开文件预览。"); }
}
