package com.x.cloudnext.server.web;

/**
 * 前端 AdminPage 启动时拉取的"我是不是管理员"轻量响应。
 * 仅暴露 isAdmin 布尔；不返回白名单成员列表或其它任何 Admin 配置内容。
 */
public class CloudNextAdminWhoamiResponse {

    private boolean admin;

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }
}
