package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.List;

public class UnifiedSearchResponse {
    public int page;
    public int size;
    public long total;
    public int totalPages;
    public boolean hasNext;
    public List<Result> items = new ArrayList<>();
    public List<Owner> owners = new ArrayList<>();
    public static class Owner {
        public String id;
        public String name;
        public Owner(String id, String name) { this.id = id; this.name = name; }
    }
    public static class Result extends DriveItemResponse {
        public String resultType;
        public String source;
        public String workspaceId = "";
        public String shareId = "";
        public String locationParentId = "";
        public String displayPath = "";
        public boolean allowPreview;
        public boolean allowDownload;
        public boolean allowCopy;
        public boolean allowLocate = true;
        public boolean allowManage;
        public boolean allowEdit;
        public boolean allowVersions;
        public String scopeType = "";
        public String scopeId = "";
        public boolean favorite;
        public boolean shared;
        public String modifiedByName = "未知";
    }
}
