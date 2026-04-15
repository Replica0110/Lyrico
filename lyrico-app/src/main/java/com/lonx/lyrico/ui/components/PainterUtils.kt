package com.lonx.lyrico.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileOutputStream

/**
 * Creates a Painter that applies a tint color to the source painter.
 * Used for theming icons that don't support direct tinting.
 */
@Composable
fun rememberTintedPainter(
    painter: Painter,
    tint: Color
): Painter = remember(tint, painter) {
    TintedPainter(painter, tint)
}

private class TintedPainter(
    private val painter: Painter,
    private val tint: Color
) : Painter() {
    override val intrinsicSize = painter.intrinsicSize

    override fun DrawScope.onDraw() {
        with(painter) {
            draw(size, colorFilter = ColorFilter.tint(tint))
        }
    }
}
class RoundedRectanglePainter(
    private val cornerRadius: Dp = 6.dp
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRoundRect(
            color = Color.White,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        )
    }
}
fun getBitmap(context: Context, data: Any): Bitmap? {
    return try {
        when (data) {
            is ByteArray -> {
                // 原生 ID3 提取出的字节数组
                BitmapFactory.decodeByteArray(data, 0, data.size)
            }
            is Uri -> {
                decodeUri(context, data)
            }
            is String -> {
                decodeUri(context, data.toUri())
            }
            is Bitmap -> {
                // 已经是 Bitmap 对象，直接返回
                data
            }
            else -> null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * 从 Uri 解析 Bitmap
 */
private fun decodeUri(context: Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
        // 裁剪操作需要获取可变(Mutable)和软件渲染(Software)的 Bitmap
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
    }
}

/**
 * 工具 2：保存裁剪好的 Bitmap 到 Cache 目录，并返回新的 Uri 供 ViewModel 使用
 */
fun saveBitmap(context: Context, bitmap: Bitmap): Uri {
    val file = File(context.cacheDir, "cropped_cover_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        // 保存为无损的 PNG 以保留最好的封面画质
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
    }
    return file.toUri()
}
@Composable
fun getSystemWallpaperColor(context: Context): Color {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val isDark = isSystemInDarkTheme()
        val dynamicScheme = if (isDark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        dynamicScheme.primary
    } else {
        MiuixTheme.colorScheme.primary
    }
}