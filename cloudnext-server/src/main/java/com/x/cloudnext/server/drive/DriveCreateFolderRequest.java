package com.x.cloudnext.server.drive;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class DriveCreateFolderRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 64)
    private String parentId = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
}
