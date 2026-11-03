package com.x.cloudnext.server.namespace;

public class DriveDescriptor {
    private String id;
    private String entryId;
    private String name;
    private boolean isDefault;
    public DriveDescriptor() {}
    public DriveDescriptor(String id, String entryId, String name) {
        this.id = id; this.entryId = entryId; this.name = name; this.isDefault = "default".equals(entryId);
    }
    public String getId() { return id; }
    public void setId(String v) { id = v; }
    public String getEntryId() { return entryId; }
    public void setEntryId(String v) { entryId = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    @com.fasterxml.jackson.annotation.JsonProperty("isDefault")
    public boolean isDefault() { return isDefault; }
    @com.fasterxml.jackson.annotation.JsonProperty("isDefault")
    public void setDefault(boolean v) { isDefault = v; }
}
