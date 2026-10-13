package com.x.cloudnext.android.feature.settings

import coil.ImageLoader
import com.x.cloudnext.android.database.dao.DriveCacheDao
import com.x.cloudnext.android.datastore.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缓存清理工具。Settings「清除缓存」触发，清下面 3 项：
 *
 * 1. Room drive_cache 全表（离线文件列表缓存）—— 下次进入文件夹会重新拉取
 * 2. Coil 磁盘缓存（图片缩略图）—— 下次显示图片会重新下载
 * 3. 搜索历史词（DataStore）—— 历史列表清空
 *
 * **不清** 的：
 * - token / session（保留登录态）
 * - transfer_task（保留运行中 / 已完成的任务记录）
 * - drive.view.mode / theme.mode / wifi.only 等用户偏好
 *
 * 全部走 IO dispatcher（Coil disk cache clear 是同步阻塞 IO）。
 */
@Singleton
class CacheCleaner @Inject constructor(
    private val driveCacheDao: DriveCacheDao,
    private val imageLoader: ImageLoader,
    private val preferences: PreferencesRepository,
) {

    /**
     * 清除所有可清缓存。完成时返回，无失败语义（任何一项失败都吞掉日志，不影响其它项）。
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        runCatching { driveCacheDao.clearAll() }
        runCatching { imageLoader.diskCache?.clear() }
        runCatching { preferences.clearSearchHistory() }
    }
}
