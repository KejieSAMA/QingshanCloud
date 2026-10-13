package com.x.cloudnext.server.drive;

import java.util.List;

/**
 * 批量打包下载请求。
 *
 * - {@link #itemIds} 是用户在前端选中的根级 id（可以是文件或文件夹）；
 *   文件夹会被后端递归展开成其内所有文件。
 * - {@link #zipName} 可选；后端会保证扩展名为 .zip。
 */
public class DriveBatchDownloadRequest {

    private List<String> itemIds;
    private String zipName;

    public List<String> getItemIds() { return itemIds; }
    public void setItemIds(List<String> itemIds) { this.itemIds = itemIds; }

    public String getZipName() { return zipName; }
    public void setZipName(String zipName) { this.zipName = zipName; }
}
