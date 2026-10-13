package com.x.cloudnext.android.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation Compose 2.8 type-safe routes。
 *
 * 顶层只有 Login / Main 两个目的地。Main 内部用 Scaffold + 4 Tab。
 * 每个 Tab 内部用一个独立的 nested NavController 管理 backstack。
 */
sealed interface CnRoute {

    @Serializable
    data object Login : CnRoute

    @Serializable
    data object Home : CnRoute

    // 文件 Tab 内 nested routes
    @Serializable
    data object MyFilesRoot : CnRoute

    @Serializable
    data class Folder(val itemId: String, val name: String) : CnRoute

    // 共享 Tab（M5a 实装）
    @Serializable
    data object SharedHost : CnRoute

    /** Shared Tab 内：进入某个 incoming folder 下钻 */
    @Serializable
    data class IncomingFolder(val shareId: String, val itemId: String, val name: String) : CnRoute

    // 协作 Tab（M5b 实装；M5a 仍占位）
    @Serializable
    data object WorkspacesPlaceholder : CnRoute

    /** 顶层 fullscreen 入口：传输任务中心 */
    @Serializable
    data object TransferCenter : CnRoute

    @Serializable
    data class TransferDetail(val taskId: String) : CnRoute

    // ---- Preview (M4) ----
    @Serializable
    data class ImagePreview(val itemId: String, val name: String) : CnRoute

    @Serializable
    data class PdfPreview(val itemId: String, val name: String) : CnRoute

    @Serializable
    data class VideoPreview(val itemId: String, val name: String) : CnRoute

    /** mode: "view" | "edit"，M4 暂只用 view */
    @Serializable
    data class OnlyofficeWeb(val itemId: String, val name: String, val mode: String) : CnRoute

    /** 纯文本类原生预览（M7）：txt/md/log/toml/yaml/json/sh/kt 等 */
    @Serializable
    data class TextPreview(val itemId: String, val name: String) : CnRoute

    // ---- M5a ----

    /** 回收站（Drawer 入口，顶层 fullscreen） */
    @Serializable
    data object TrashRoot : CnRoute

    /** 搜索（顶层 fullscreen） */
    @Serializable
    data object SearchRoot : CnRoute

    /**
     * 创建分享（顶层 fullscreen）。itemIds 用逗号拼接（typesafe route 不直接支持 List）。
     * OrgPicker 选完后 savedStateHandle["share.targets"] 写 JSON 字符串，由 CreateShareScreen 解析。
     */
    @Serializable
    data class CreateShare(val itemIdsCsv: String) : CnRoute

    /**
     * 组织搜索（顶层 fullscreen）。
     * @param mode "multi"（默认，分享对象多选）或 "single"（添加成员等场景）
     * @param resultKey 单选 mode 返回结果写到 previousBackStackEntry.savedStateHandle 的 key
     */
    @Serializable
    data class OrgPicker(
        val mode: String = "multi",
        val resultKey: String = "share.targets",
    ) : CnRoute

    // ---- M5b ----

    /** Workspaces Tab 根：列表 */
    @Serializable
    data object WorkspacesRoot : CnRoute

    /** 工作空间详情（根目录） */
    @Serializable
    data class WorkspaceDetail(val workspaceId: String, val name: String) : CnRoute

    /**
     * 工作空间子目录。parentId 对应 ViewModel.ARG_PARENT_ID（要列出的目录的 itemId）。
     */
    @Serializable
    data class WorkspaceFolder(
        val workspaceId: String,
        val parentId: String,
        val name: String,
    ) : CnRoute

    /** 工作空间成员管理 */
    @Serializable
    data class WorkspaceMembers(
        val workspaceId: String,
        val name: String,
    ) : CnRoute

    /** 创建外链（顶层 fullscreen） */
    @Serializable
    data class CreateLink(val itemId: String, val fileName: String) : CnRoute

    /** 外链访问记录（顶层 fullscreen） */
    @Serializable
    data class AccessLogs(val linkId: String, val fileName: String) : CnRoute

    // ---- M6 ----

    /** 设置（Drawer 入口，顶层 fullscreen） */
    @Serializable
    data object Settings : CnRoute

    /** 关于 App（Settings → 关于 入口，顶层 fullscreen） */
    @Serializable
    data object About : CnRoute

    /** 外链管理（Drawer 入口，顶层 fullscreen）。M6 UI 对齐：原 Shared Tab 的"我的外链" chip 拆出独立 Screen。 */
    @Serializable
    data object LinksRoot : CnRoute
}
