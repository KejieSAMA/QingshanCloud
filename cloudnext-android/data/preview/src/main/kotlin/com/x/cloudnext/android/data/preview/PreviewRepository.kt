package com.x.cloudnext.android.data.preview

import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.network.api.DriveApi
import com.x.cloudnext.android.network.runCatchingApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预览仓库。M4：调 /api/drive/items/{id}/preview。
 *
 * Office 类型才需要走这个接口拿 previewPageUrl；图片/PDF/视频客户端按文件类型直接
 * 走 /api/drive/items/{id}/content 流式读，不依赖此 API。
 */
@Singleton
class PreviewRepository @Inject constructor(
    private val driveApi: DriveApi,
) {
    suspend fun getPreview(itemId: String): CnResult<DrivePreview> = runCatchingApi {
        driveApi.getPreview(itemId).toDomain()
    }
}
