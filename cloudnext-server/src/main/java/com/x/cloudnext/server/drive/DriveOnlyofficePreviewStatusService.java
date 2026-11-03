package com.x.cloudnext.server.drive;

import java.util.Collections;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.config.CloudNextProperties;

/** Compatibility response for existing /status and /config clients, with no DocumentServer dependency. */
@Service
public class DriveOnlyofficePreviewStatusService {
    public DriveOnlyofficePreviewStatusService(CloudNextProperties ignored) {}
    public DriveOnlyofficePreviewReadiness getReadiness(){return new DriveOnlyofficePreviewReadiness(false,false,Collections.emptyList());}
    public DriveOnlyofficePreviewReadiness getEditReadiness(){return getReadiness();}
    public void requireConfigured(){retired();}
    public void requireEditConfigured(){retired();}
    public void requireEditEnabled(){retired();}
    private void retired(){throw new ResponseStatusException(HttpStatus.GONE,"在线编辑已停用，请使用文件预览。");}
}
