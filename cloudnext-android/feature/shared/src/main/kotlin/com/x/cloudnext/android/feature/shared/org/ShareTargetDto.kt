package com.x.cloudnext.android.feature.shared.org

import com.x.cloudnext.android.data.share.ShareTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * OrgPicker 返回结果用的可序列化载体。
 *
 * Navigation Compose 2.8 typesafe Route 不便传 List<ShareTarget>（ShareTarget 来自 :data:share，
 * 跨 ViewModel 通信需要 JSON 串）。这里定义 wire 格式 + 工具。
 */
@Serializable
data class ShareTargetWire(
    val type: String,
    val id: String,
    val name: String,
)

object ShareTargetCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(ShareTargetWire.serializer())

    fun encode(targets: List<ShareTarget>): String =
        json.encodeToString(listSerializer, targets.map {
            ShareTargetWire(type = it.type.raw, id = it.id, name = it.name)
        })

    fun decode(raw: String?): List<ShareTarget> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(listSerializer, raw).map {
                ShareTarget(
                    type = ShareTarget.TargetType.parse(it.type),
                    id = it.id,
                    name = it.name,
                )
            }
        }.getOrDefault(emptyList())
    }
}
