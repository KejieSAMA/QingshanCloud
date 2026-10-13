package com.x.cloudnext.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.common.FileKind
import com.x.cloudnext.android.common.formatFileSize
import com.x.cloudnext.android.common.formatRelativeTime
import com.x.cloudnext.android.common.guessFileKind
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 文件卡片 - 列表模式。视觉与交互严格对照 cloudnext-web/src/components/mobile/MobileFileCard.vue。
 *
 * 布局：40dp 图标 | 1fr (名称 + 元数据) | auto (操作槽)。
 * 单击：onClick；长按 480ms（移动 < 10dp）：onLongPress。
 *
 * 视觉规则（M6 风格统一）：
 *  - 选中态背景：实色 CnPalette.PrimarySoft（不是 alpha 0.4f 半透明）
 *  - 卡内自带底部 hairline divider（caller 不再加外部 HorizontalDivider）；divider 距离左侧 64dp，与文件名对齐
 *  - padding：12dp horizontal × 8dp vertical = mobile.css --m-sp-3 × --m-sp-2
 *  - 圆角：图标方块用 MaterialTheme.shapes.medium（12dp）
 *
 * @param showDivider 是否画底部 hairline 分隔线（最后一项可关闭）
 */
@Composable
fun CnFileCard(
    name: String,
    isFolder: Boolean,
    modifier: Modifier = Modifier,
    fileType: String? = null,
    sizeBytes: Long? = null,
    updatedAt: String? = null,
    childCount: Int? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    showDivider: Boolean = true,
    onClick: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    val tokens = LocalCnTokens.current
    val kind = remember(isFolder, fileType, name) {
        if (isFolder) FileKind.FOLDER
        else fileType?.takeIf { it.isNotBlank() } ?: guessFileKind(name)
    }
    val iconBg = tokens.fileIcon.background(kind)
    val iconFg = tokens.fileIcon.foreground(kind)
    val meta = remember(isFolder, sizeBytes, updatedAt, childCount) {
        buildMeta(
            isFolder = isFolder,
            sizeBytes = sizeBytes,
            updatedAt = updatedAt,
            childCount = childCount,
        )
    }
    val rowBackground = if (selected) CnPalette.PrimarySoft else Color.Transparent

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = tokens.dimensions.fileCardMinHeight)
                .background(rowBackground)
                .then(
                    if (onLongPress != null) {
                        Modifier.longPressGesture(
                            thresholdMs = tokens.longPressMs,
                            slopDp = tokens.longPressSlopDp,
                            onLongPress = onLongPress,
                            onTap = onClick,
                        )
                    } else {
                        Modifier.clickable(onClick = onClick)
                    },
                )
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(tokens.dimensions.fileIconBox)
                    .background(iconBg, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = kind.first().uppercase(),
                    color = iconFg,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = tokens.spacing.md),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CnPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = CnPalette.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when {
                selecting -> SelectionRadio(selected = selected)
                onMore != null -> Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "⋯",
                        color = CnPalette.TextTertiary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        // hairline divider：与文件名左对齐（64dp = 12dp padding + 40dp icon + 12dp gap）
        if (showDivider) {
            HorizontalDivider(
                color = CnPalette.DividerHairline,
                modifier = Modifier.padding(start = 64.dp),
            )
        }
    }
}

@Composable
private fun SelectionRadio(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = if (selected) CnPalette.Primary else Color.Transparent,
                shape = CircleShape,
            )
            .then(
                if (selected) Modifier
                else Modifier.border(width = 1.5.dp, color = CnPalette.OutlineStrong, shape = CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                text = "✓",
                color = CnPalette.OnPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun buildMeta(
    isFolder: Boolean,
    sizeBytes: Long?,
    updatedAt: String?,
    childCount: Int?,
): String {
    val parts = mutableListOf<String>()
    if (isFolder) {
        if (childCount != null) parts.add("$childCount 项")
    } else if (sizeBytes != null && sizeBytes >= 0) {
        parts.add(formatFileSize(sizeBytes))
    }
    val time = formatRelativeTime(updatedAt)
    if (time.isNotEmpty()) parts.add(time)
    return parts.joinToString(" · ")
}

/**
 * 长按手势 + 单击。LONG_PRESS_MS=480、MOVE_TOLERANCE=10dp 严格对齐 Web。
 *
 * 自实现而非 detectTapGestures：后者长按阈值默认 500ms 且不可调；这里用 awaitEachGesture
 * + launch 计时器 + slop 监听，配置严格符合 mobile.css 约定。
 *
 * coroutine scope 取 PointerInputScope（继承 CoroutineScope），AwaitPointerEventScope 不能
 * 直接 launch。
 */
private fun Modifier.longPressGesture(
    thresholdMs: Long,
    slopDp: Dp,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
): Modifier = pointerInput(thresholdMs, slopDp) {
    val slopPx = slopDp.toPx()
    coroutineScope {
        val scope: CoroutineScope = this
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val startX = down.position.x
            val startY = down.position.y
            var triggered = false
            var canceled = false

            val timer = scope.launch {
                delay(thresholdMs)
                if (!canceled) {
                    triggered = true
                    onLongPress()
                }
            }

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        if (!triggered && !canceled) onTap()
                        break
                    }
                    val dx = abs(change.position.x - startX)
                    val dy = abs(change.position.y - startY)
                    if (dx > slopPx || dy > slopPx) {
                        canceled = true
                        break
                    }
                }
            } finally {
                timer.cancel()
            }
        }
    }
}
