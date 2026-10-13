package com.x.cloudnext.android.feature.preview.video

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.x.cloudnext.android.network.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class VideoPreviewViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>(ARG_ITEM_ID).orEmpty()

    /**
     * 在 UI 层 Compose 里调用一次创建 Player；返回值需要在 DisposableEffect onDispose 中
     * 调 player.release()。
     */
    fun createPlayer(context: Context): ExoPlayer {
        val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/api/drive/items/" + itemId + "/content"
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient::newCall)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }

    companion object {
        const val ARG_ITEM_ID = "itemId"
    }
}
