package com.x.cloudnext.server.drive;

public class DriveSearchRequest {

    private String scope = "personal";
    private String keyword = "";
    private String itemType = "";
    private String owner = "";
    private String updatedFrom = "";
    private String updatedTo = "";
    /** N+2.5: 文件类型筛选（word/excel/ppt/pdf/image/video/archive/other）；空=不过滤 */
    private String fileType = "";
    private Integer page = 1;
    private Integer size = 20;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getUpdatedFrom() {
        return updatedFrom;
    }

    public void setUpdatedFrom(String updatedFrom) {
        this.updatedFrom = updatedFrom;
    }

    public String getUpdatedTo() {
        return updatedTo;
    }

    public void setUpdatedTo(String updatedTo) {
        this.updatedTo = updatedTo;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType == null ? "" : fileType;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
