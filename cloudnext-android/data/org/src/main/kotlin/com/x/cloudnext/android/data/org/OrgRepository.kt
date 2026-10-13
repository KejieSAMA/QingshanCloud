package com.x.cloudnext.android.data.org

import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.network.api.OrgApi
import com.x.cloudnext.android.network.runCatchingApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 组织搜索仓库。无缓存（搜索关键词敏感 + 后端数据可能变更）。
 *
 * 上层（OrgPickerViewModel）需要 debounce 300ms 再调，避免每按一键发 RPC。
 */
@Singleton
class OrgRepository @Inject constructor(
    private val orgApi: OrgApi,
) {

    suspend fun search(
        type: OrgSearchResult.Type,
        keyword: String,
    ): CnResult<List<OrgSearchResult>> = runCatchingApi {
        orgApi.search(type = type.raw, keyword = keyword).map { dto ->
            OrgSearchResult(
                type = OrgSearchResult.Type.parse(dto.type),
                id = dto.id.orEmpty(),
                name = dto.name.orEmpty(),
                distinguishedName = dto.distinguishedName,
                path = dto.path,
            )
        }
    }
}
