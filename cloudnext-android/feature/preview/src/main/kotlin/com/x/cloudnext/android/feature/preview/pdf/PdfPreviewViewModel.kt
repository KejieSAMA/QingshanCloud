package com.x.cloudnext.android.feature.preview.pdf

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.network.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

sealed interface PdfUiState {
    data object Loading : PdfUiState
    data class Ready(val file: File) : PdfUiState
    data class Failed(val message: String) : PdfUiState
}

@HiltViewModel
class PdfPreviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>(ARG_ITEM_ID).orEmpty()

    private val _state = MutableStateFlow<PdfUiState>(PdfUiState.Loading)
    val state: StateFlow<PdfUiState> = _state.asStateFlow()

    init {
        downloadAndPrepare()
    }

    private fun downloadAndPrepare() {
        viewModelScope.launch {
            _state.value = PdfUiState.Loading
            val result = withContext(Dispatchers.IO) {
                runCatching { downloadToCache() }
            }
            _state.value = result.fold(
                onSuccess = { PdfUiState.Ready(it) },
                onFailure = {
                    Log.w("PdfPreview", "pdf download failed", it)
                    PdfUiState.Failed(it.message ?: "PDF 加载失败")
                },
            )
        }
    }

    private fun downloadToCache(): File {
        val dir = File(context.cacheDir, "preview").apply { mkdirs() }
        val file = File(dir, "$itemId.pdf")
        if (file.exists() && file.length() > 0) return file

        val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/api/drive/items/" + itemId + "/content"
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                file.delete()
                error("HTTP ${response.code}")
            }
            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("响应体为空")
        }
        return file
    }

    fun retry() = downloadAndPrepare()

    companion object {
        const val ARG_ITEM_ID = "itemId"
    }
}
