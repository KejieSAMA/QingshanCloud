package com.x.cloudnext.android.data.search

import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.drive.toDomain
import com.x.cloudnext.android.datastore.PreferencesRepository
import com.x.cloudnext.android.network.api.SearchApi
import com.x.cloudnext.android.network.runCatchingApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索仓库。无 Room 缓存（搜索结果时效性强）+ DataStore 存历史词。
 *
 * 历史词存储委托 [PreferencesRepository]，去重 / LRU / 截断 10 条均在那里处理。
 */
@Singleton
class SearchRepository @Inject constructor(
    private val searchApi: SearchApi,
    private val preferences: PreferencesRepository,
) {

    /** 历史词 Flow。最新在前。 */
    val historyFlow: Flow<List<String>> = preferences.searchHistoryFlow

    suspend fun search(
        keyword: String,
        scope: String = "personal",
        fileType: SearchFileType = SearchFileType.All,
        page: Int = 1,
        size: Int = 20,
    ): CnResult<SearchPage> = runCatchingApi {
        val response = searchApi.searchFiles(
            keyword = keyword,
            scope = scope,
            fileType = fileType.value,
            page = page,
            size = size,
        )
        SearchPage(
            items = response.items.map { it.toDomain() },
            page = response.page,
            size = response.size,
            total = response.total,
            totalPages = response.totalPages,
            hasNext = response.hasNext,
        )
    }

    /** 写入一条搜索历史词（去重 / LRU）。 */
    suspend fun rememberKeyword(keyword: String) {
        preferences.addSearchHistory(keyword)
    }

    suspend fun removeKeyword(keyword: String) {
        preferences.removeSearchHistory(keyword)
    }

    suspend fun clearHistory() {
        preferences.clearSearchHistory()
    }
}
