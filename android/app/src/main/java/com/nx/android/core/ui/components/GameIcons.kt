package com.nx.android.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nx.android.data.GameIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 游戏封面：降采样读一张图，主线程之外（capyps5 rememberImage 的手法）。
 *
 * 库页的头图与「存储」分区的缩略图都用它。
 */
@Composable
fun rememberGameIcon(path: String?, maxSize: Int): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isNullOrEmpty()) null else withContext(Dispatchers.IO) {
            runCatching { GameIcon.decode(path, maxSize)?.asImageBitmap() }.getOrNull()
        }
    }
