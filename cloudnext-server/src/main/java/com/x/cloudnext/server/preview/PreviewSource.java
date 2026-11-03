package com.x.cloudnext.server.preview;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

/** An immutable, already authorized version. Never constructed from client paths. */
public final class PreviewSource {
    public final String driveId, itemId, versionId, name, contentType, identity, kind;
    public final Path path;
    public final long size;
    public PreviewSource(String driveId, String itemId, String versionId, String storageKey,
                         String name, String contentType, Path path, long size) {
        this.driveId=driveId; this.itemId=itemId; this.versionId=versionId; this.name=name;
        this.contentType=contentType; this.path=path; this.size=size; this.kind=kind(name,contentType);
        // A rename/type change also invalidates a viewer; uploads use immutable storage keys.
        this.identity=hash(String.join("\n", driveId,itemId,versionId,storageKey,name,contentType,String.valueOf(size),String.valueOf(path.toFile().lastModified())));
    }
    public static String extension(String name) {
        int i=name==null?-1:name.lastIndexOf('.'); return i<0?"":name.substring(i+1).toLowerCase(Locale.ROOT);
    }
    public static String kind(String name, String type) {
        String ext=extension(name), mime=type==null?"":type.toLowerCase(Locale.ROOT);
        if(Set.of("doc","docx","xls","xlsx","ppt","pptx").contains(ext)) return "office";
        if(ext.equals("pdf")||mime.equals("application/pdf")) return "pdf";
        if(Set.of("png","jpg","jpeg","gif","bmp","webp").contains(ext)) return "image";
        if(Set.of("txt","md","csv","json","log").contains(ext)) return "text";
        return "unsupported";
    }
    public static String hash(String text) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static String hex(byte[] bytes) { StringBuilder s=new StringBuilder(); for(byte b:bytes)s.append(String.format("%02x",b)); return s.toString(); }
}
