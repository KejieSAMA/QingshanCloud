package com.x.cloudnext.android.network.model

import kotlinx.serialization.Serializable

/**
 * 组织搜索结果。对应 cloudnext-server OrgSearchResultResponse。
 *
 * type 取值：person / department
 * distinguishedName 是 O2 OA 的 DN（用于唯一标识身份）。
 * path 是部门完整路径（仅 department 类型有意义）。
 */
@Serializable
data class OrgSearchResultResponse(
    val type: String? = null,
    val id: String? = null,
    val name: String? = null,
    val distinguishedName: String? = null,
    val path: String? = null,
)
