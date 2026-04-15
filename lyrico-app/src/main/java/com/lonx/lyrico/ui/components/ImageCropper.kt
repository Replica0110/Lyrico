package com.lonx.lyrico.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.lonx.lyrico.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ============================================================
// 裁剪状态持有者 —— 供外部读取和触发裁剪
// ============================================================
class ImageCropperState internal constructor(
    internal val bitmap: Bitmap
) {
    internal var imageBounds by mutableStateOf(Rect.Zero)
    internal var cropRect by mutableStateOf(Rect.Zero)
    var aspectRatio by mutableStateOf<Float?>(1f)

    /**
     * 外部调用此方法执行裁剪，返回裁剪后的 Bitmap
     */
    fun crop(): Bitmap {
        return cropActualBitmap(bitmap, imageBounds, cropRect)
    }
}

/**
 * 创建并记住一个 ImageCropperState
 */
@Composable
fun rememberImageCropperState(bitmap: Bitmap): ImageCropperState {
    return remember(bitmap) { ImageCropperState(bitmap) }
}

enum class DragHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT, CENTER, NONE
}

@Composable
fun ImageCropper(
    state: ImageCropperState,
    modifier: Modifier = Modifier,
    showRatioBar: Boolean = true
) {
    val bitmap = state.bitmap
    var viewSize by remember { mutableStateOf(Size.Zero) }

    val density = LocalDensity.current
    val touchTolerance = with(density) { 24.dp.toPx() }
    val minCropSize = with(density) { 60.dp.toPx() }

    // 缓存 ImageBitmap
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    // 记住初始宽高比，避免重组期间变化
    val bitmapAspectRatio = remember(bitmap) {
        bitmap.width.toFloat() / bitmap.height.toFloat()
    }
    // 计算图片在视图中的显示区域
    LaunchedEffect(viewSize, bitmap) {
        if (viewSize.width == 0f || viewSize.height == 0f) return@LaunchedEffect
        val scale = min(
            viewSize.width / bitmap.width,
            viewSize.height / bitmap.height
        )
        val imageWidth = bitmap.width * scale
        val imageHeight = bitmap.height * scale
        val left = (viewSize.width - imageWidth) / 2f
        val top = (viewSize.height - imageHeight) / 2f
        state.imageBounds = Rect(left, top, left + imageWidth, top + imageHeight)

        // 初始化裁剪框
        val initSize = min(imageWidth, imageHeight) * 0.8f
        val ratio = state.aspectRatio
        val (w, h) = if (ratio != null) {
            if (ratio >= 1f) {
                val w = initSize; w to w / ratio
            } else {
                val h = initSize; (h * ratio) to h
            }
        } else {
            initSize to initSize
        }
        state.cropRect = Rect(
            left = (viewSize.width - w) / 2f,
            top = (viewSize.height - h) / 2f,
            right = (viewSize.width + w) / 2f,
            bottom = (viewSize.height + h) / 2f
        )
    }

    // 比例切换时调整
    LaunchedEffect(state.aspectRatio) {
        if (state.cropRect.width <= 0 || state.imageBounds.width <= 0) return@LaunchedEffect
        val ratio = state.aspectRatio ?: return@LaunchedEffect
        val ib = state.imageBounds
        val center = state.cropRect.center

        val candidateW = min(state.cropRect.width, ib.width)
        val candidateH = candidateW / ratio
        val (nw, nh) = if (candidateH <= ib.height) {
            candidateW to candidateH
        } else {
            val h = min(state.cropRect.height, ib.height)
            (h * ratio) to h
        }

        var l = center.x - nw / 2;
        var t = center.y - nh / 2
        var r = center.x + nw / 2;
        var b = center.y + nh / 2
        if (l < ib.left) {
            l = ib.left; r = l + nw
        }
        if (r > ib.right) {
            r = ib.right; l = r - nw
        }
        if (t < ib.top) {
            t = ib.top; b = t + nh
        }
        if (b > ib.bottom) {
            b = ib.bottom; t = b - nh
        }
        state.cropRect = Rect(l, t, r, b)
    }

    // 用 rememberUpdatedState 避免 pointerInput 重启
    val cropRectRef = rememberUpdatedState(state.cropRect)
    val imageBoundsRef = rememberUpdatedState(state.imageBounds)
    val aspectRatioRef = rememberUpdatedState(state.aspectRatio)
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    val freeRatio = stringResource(id = R.string.option_free_ratio)
    Column(modifier = modifier.fillMaxWidth()) {
        // 可选的比例选择栏
        if (showRatioBar) {
            val ratioOptions = remember {
                listOf(
                    null to freeRatio,
                    1f to "1:1",
                    4f / 3f to "4:3",
                    16f / 9f to "16:9"
                )
            }

            val spinnerItems = remember {
                ratioOptions.map { (_, label) ->
                    SpinnerEntry(title = label)
                }
            }

            val selectedIndex = remember(state.aspectRatio) {
                ratioOptions.indexOfFirst { it.first == state.aspectRatio }.coerceAtLeast(0)
            }

            Card(
                modifier = Modifier.padding(bottom = 8.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer
                )
            ) {
                WindowSpinnerPreference(
                    items = spinnerItems,
                    selectedIndex = selectedIndex,
                    title = stringResource(R.string.label_image_ratio),
                    onSelectedIndexChange = { index ->
                        state.aspectRatio = ratioOptions[index].first
                    }
                )
            }
        }


        // 裁剪画布
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmapAspectRatio)
                .clipToBounds()
                .background(Color.Black)
                .onGloballyPositioned { viewSize = it.size.toSize() }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            activeHandle = getHitHandle(offset, cropRectRef.value, touchTolerance)
                        },
                        onDragEnd = { activeHandle = DragHandle.NONE },
                        onDragCancel = { activeHandle = DragHandle.NONE },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            state.cropRect = calculateNewRect(
                                handle = activeHandle,
                                oldRect = cropRectRef.value,
                                dragAmount = dragAmount,
                                bounds = imageBoundsRef.value,
                                minSize = minCropSize,
                                aspectRatio = aspectRatioRef.value
                            )
                        }
                    )
                }
        )  {
            Canvas(modifier = Modifier.fillMaxWidth()) {
                val ib = state.imageBounds
                val cr = state.cropRect
                if (ib.isEmpty) return@Canvas

                // ① 绘制底图
                drawImage(
                    image = imageBitmap,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(ib.left.toInt(), ib.top.toInt()),
                    dstSize = IntSize(ib.width.toInt(), ib.height.toInt())
                )

                // ② 四块遮罩（不覆盖裁剪框内部）
                val overlay = Color.Black.copy(alpha = 0.6f)
                // 上
                if (cr.top > ib.top) {
                    drawRect(overlay, Offset(ib.left, ib.top), Size(ib.width, cr.top - ib.top))
                }
                // 下
                if (cr.bottom < ib.bottom) {
                    drawRect(
                        overlay,
                        Offset(ib.left, cr.bottom),
                        Size(ib.width, ib.bottom - cr.bottom)
                    )
                }
                // 左
                if (cr.left > ib.left) {
                    drawRect(
                        overlay,
                        Offset(ib.left, cr.top),
                        Size(cr.left - ib.left, cr.height)
                    )
                }
                // 右
                if (cr.right < ib.right) {
                    drawRect(
                        overlay,
                        Offset(cr.right, cr.top),
                        Size(ib.right - cr.right, cr.height)
                    )
                }

                // ③ 白色边框
                drawRect(Color.White, cr.topLeft, cr.size, style = Stroke(2.dp.toPx()))

                // ④ 三分网格线
                drawGridLines(cr)

                // ⑤ 四角装饰
                drawCorners(cr)
            }
        }
    }
}

// ============================================================
// 工具函数
// ============================================================

fun getHitHandle(touch: Offset, rect: Rect, tolerance: Float): DragHandle {
    val leftHit = touch.x in (rect.left - tolerance)..(rect.left + tolerance)
    val rightHit = touch.x in (rect.right - tolerance)..(rect.right + tolerance)
    val topHit = touch.y in (rect.top - tolerance)..(rect.top + tolerance)
    val bottomHit = touch.y in (rect.bottom - tolerance)..(rect.bottom + tolerance)

    return when {
        leftHit && topHit -> DragHandle.TOP_LEFT
        rightHit && topHit -> DragHandle.TOP_RIGHT
        leftHit && bottomHit -> DragHandle.BOTTOM_LEFT
        rightHit && bottomHit -> DragHandle.BOTTOM_RIGHT
        leftHit && touch.y in rect.top..rect.bottom -> DragHandle.LEFT
        rightHit && touch.y in rect.top..rect.bottom -> DragHandle.RIGHT
        topHit && touch.x in rect.left..rect.right -> DragHandle.TOP
        bottomHit && touch.x in rect.left..rect.right -> DragHandle.BOTTOM
        touch.x in rect.left..rect.right && touch.y in rect.top..rect.bottom -> DragHandle.CENTER
        else -> DragHandle.NONE
    }
}

fun calculateNewRect(
    handle: DragHandle,
    oldRect: Rect,
    dragAmount: Offset,
    bounds: Rect,
    minSize: Float,
    aspectRatio: Float?
): Rect {
    if (handle == DragHandle.NONE) return oldRect

    val safeMinW = min(minSize, bounds.width)
    val safeMinH = min(minSize, bounds.height)

    var left = oldRect.left
    var top = oldRect.top
    var right = oldRect.right
    var bottom = oldRect.bottom

    if (handle == DragHandle.CENTER) {

        // 计算平移量的最大允许范围
        val maxDx = (bounds.right - right).coerceAtLeast(0f)
        val minDx = (bounds.left - left).coerceAtMost(0f)
        val maxDy = (bounds.bottom - bottom).coerceAtLeast(0f)
        val minDy = (bounds.top - top).coerceAtMost(0f)

        val dx = dragAmount.x.coerceIn(minDx, maxDx)
        val dy = dragAmount.y.coerceIn(minDy, maxDy)

        return oldRect.translate(dx, dy)
    }

    when (handle) {
        DragHandle.TOP_LEFT -> { left += dragAmount.x; top += dragAmount.y }
        DragHandle.TOP_RIGHT -> { right += dragAmount.x; top += dragAmount.y }
        DragHandle.BOTTOM_LEFT -> { left += dragAmount.x; bottom += dragAmount.y }
        DragHandle.BOTTOM_RIGHT -> { right += dragAmount.x; bottom += dragAmount.y }
        DragHandle.LEFT -> left += dragAmount.x
        DragHandle.RIGHT -> right += dragAmount.x
        DragHandle.TOP -> top += dragAmount.y
        DragHandle.BOTTOM -> bottom += dragAmount.y
    }

    left = left.coerceIn(bounds.left, maxOf(bounds.left, right - safeMinW))
    right = right.coerceIn(minOf(bounds.right, left + safeMinW), bounds.right)
    top = top.coerceIn(bounds.top, maxOf(bounds.top, bottom - safeMinH))
    bottom = bottom.coerceIn(minOf(bounds.bottom, top + safeMinH), bounds.bottom)


    if (aspectRatio != null) {
        var w = right - left
        var h = bottom - top

        val useWidthAsBasis = when (handle) {
            DragHandle.LEFT, DragHandle.RIGHT -> true
            DragHandle.TOP, DragHandle.BOTTOM -> false
            else -> abs(dragAmount.x) >= abs(dragAmount.y)
        }

        if (useWidthAsBasis) {
            h = w / aspectRatio
            if (h > bounds.height) { h = bounds.height; w = h * aspectRatio }
            if (h < safeMinH) { h = safeMinH; w = h * aspectRatio }
        } else {
            w = h * aspectRatio
            if (w > bounds.width) { w = bounds.width; h = w / aspectRatio }
            if (w < safeMinW) { w = safeMinW; h = w / aspectRatio }
        }

        // 根据手柄重新定位
        when (handle) {
            DragHandle.TOP_LEFT -> { left = right - w; top = bottom - h }
            DragHandle.TOP_RIGHT -> { right = left + w; top = bottom - h }
            DragHandle.BOTTOM_LEFT -> { left = right - w; bottom = top + h }
            DragHandle.BOTTOM_RIGHT -> { right = left + w; bottom = top + h }
            DragHandle.LEFT -> {
                left = right - w
                val cy = (top + bottom) / 2f
                top = cy - h / 2f; bottom = cy + h / 2f
            }
            DragHandle.RIGHT -> {
                right = left + w
                val cy = (top + bottom) / 2f
                top = cy - h / 2f; bottom = cy + h / 2f
            }
            DragHandle.TOP -> {
                top = bottom - h
                val cx = (left + right) / 2f
                left = cx - w / 2f; right = cx + w / 2f
            }
            DragHandle.BOTTOM -> {
                bottom = top + h
                val cx = (left + right) / 2f
                left = cx - w / 2f; right = cx + w / 2f
            }
        }

        if (left < bounds.left) {
            val offset = bounds.left - left
            left += offset; right += offset
        }
        if (right > bounds.right) {
            val offset = right - bounds.right
            left -= offset; right -= offset
        }
        if (top < bounds.top) {
            val offset = bounds.top - top
            top += offset; bottom += offset
        }
        if (bottom > bounds.bottom) {
            val offset = bottom - bounds.bottom
            top -= offset; bottom -= offset
        }

        // 6. 最后的极端情况兜底：如果比例裁剪框依然大于图片，强制缩小
        if (right - left > bounds.width) {
            left = bounds.left
            right = bounds.right
        }
        if (bottom - top > bounds.height) {
            top = bounds.top
            bottom = bounds.bottom
        }
    }

    return Rect(left, top, right, bottom)
}

fun cropActualBitmap(originalBitmap: Bitmap, imageDisplayBounds: Rect, cropRect: Rect): Bitmap {
    val scaleX = originalBitmap.width / imageDisplayBounds.width
    val scaleY = originalBitmap.height / imageDisplayBounds.height

    val cropX = ((cropRect.left - imageDisplayBounds.left) * scaleX).toInt()
    val cropY = ((cropRect.top - imageDisplayBounds.top) * scaleY).toInt()
    val cropWidth = (cropRect.width * scaleX).toInt()
    val cropHeight = (cropRect.height * scaleY).toInt()

    val safeX = max(0, cropX)
    val safeY = max(0, cropY)
    val safeWidth = min(originalBitmap.width - safeX, cropWidth).coerceAtLeast(1)
    val safeHeight = min(originalBitmap.height - safeY, cropHeight).coerceAtLeast(1)

    return Bitmap.createBitmap(originalBitmap, safeX, safeY, safeWidth, safeHeight)
}

fun DrawScope.drawGridLines(rect: Rect) {
    val thirdW = rect.width / 3f
    val thirdH = rect.height / 3f
    val gridColor = Color.White.copy(alpha = 0.4f)
    val gridStroke = 1.dp.toPx()
    for (i in 1..2) {
        val x = rect.left + thirdW * i
        drawLine(gridColor, Offset(x, rect.top), Offset(x, rect.bottom), gridStroke)
    }
    for (i in 1..2) {
        val y = rect.top + thirdH * i
        drawLine(gridColor, Offset(rect.left, y), Offset(rect.right, y), gridStroke)
    }
}

fun DrawScope.drawCorners(rect: Rect) {
    val length = 24.dp.toPx()
    val strokeWidth = 4.dp.toPx()
    val color = Color.White
    drawLine(color, Offset(rect.left, rect.top), Offset(rect.left + length, rect.top), strokeWidth)
    drawLine(color, Offset(rect.left, rect.top), Offset(rect.left, rect.top + length), strokeWidth)
    drawLine(
        color,
        Offset(rect.right, rect.top),
        Offset(rect.right - length, rect.top),
        strokeWidth
    )
    drawLine(
        color,
        Offset(rect.right, rect.top),
        Offset(rect.right, rect.top + length),
        strokeWidth
    )
    drawLine(
        color,
        Offset(rect.left, rect.bottom),
        Offset(rect.left + length, rect.bottom),
        strokeWidth
    )
    drawLine(
        color,
        Offset(rect.left, rect.bottom),
        Offset(rect.left, rect.bottom - length),
        strokeWidth
    )
    drawLine(
        color,
        Offset(rect.right, rect.bottom),
        Offset(rect.right - length, rect.bottom),
        strokeWidth
    )
    drawLine(
        color,
        Offset(rect.right, rect.bottom),
        Offset(rect.right, rect.bottom - length),
        strokeWidth
    )
}