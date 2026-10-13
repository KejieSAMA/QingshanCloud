package com.x.cloudnext.android.data.org

/**
 * 组织搜索结果（人 / 部门）。OrgPicker 用。
 */
data class OrgSearchResult(
    val type: Type,
    val id: String,
    val name: String,
    val distinguishedName: String?,
    val path: String?,
) {
    enum class Type(val raw: String) {
        Person("person"),
        Department("department"),
        ;

        companion object {
            fun parse(raw: String?): Type = when (raw?.lowercase()) {
                "department", "dept" -> Department
                else -> Person
            }
        }
    }
}
