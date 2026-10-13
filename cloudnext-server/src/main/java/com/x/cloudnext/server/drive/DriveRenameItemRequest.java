package com.x.cloudnext.server.drive;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class DriveRenameItemRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
