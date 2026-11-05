package com.flxrs.dankchat.ui.chat.messages.common

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintShadowUi
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintShape
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintUi
import com.flxrs.dankchat.ui.chat.EmoteUi
import com.flxrs.dankchat.ui.chat.messages.PaintTextures
import com.flxrs.dankchat.ui.chat.messages.rememberPaintTextures
import com.flxrs.dankchat.ui.chat.messages.toBrush
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class EmoteDimensions(
    val id: String,
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * Text with badges, emotes and spacers in placeholders sized by [knownDimensions]. The images are drawn by [inlineImages]
 * instead of being composed, and taps on an emote go to [onEmoteClick].
 */
@Composable
internal fun TextWithMeasuredInlineContent(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    inlineImages: InlineImages? = null,
    knownDimensions: ImmutableMap<String, EmoteDimensions> = persistentMapOf(),
    namePaint: SevenTVPaintUi? = null,
    nameRange: TextRange? = null,
    nameColor: Color = Color.Unspecified,
    onTextClick: ((Int) -> Unit)? = null,
    onBackgroundClick: (() -> Unit)? = null,
    onTextLongClick: ((Int) -> Unit)? = null,
    onEmoteClick: ((EmoteUi) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val placeholders = remember(knownDimensions, text, density) { buildPlaceholders(knownDimensions, text, density) }
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val coroutineScope = rememberCoroutineScope()

    val paintedName =
        remember(namePaint, nameRange, text) {
            val range = nameRange
            if (namePaint == null || range == null || range.max <= range.min) {
                null
            } else {
                text.text.substring(range.min, range.max)
            }
        }
    // Measured inside the layout pass, because the name has to fit the width the row actually has. An
    // unconstrained measurement is wider than the row for long names and the paint then gets clipped.
    val paintedNameLayout = remember { mutableStateOf<PaintedName?>(null) }
    val measuredText = remember { mutableStateOf<MeasuredText?>(null) }
    val currentInlineImages = rememberUpdatedState(inlineImages)
    val currentOnTextClick = rememberUpdatedState(onTextClick)
    val currentOnBackgroundClick = rememberUpdatedState(onBackgroundClick)
    val currentOnTextLongClick = rememberUpdatedState(onTextLongClick)
    val currentOnEmoteClick = rememberUpdatedState(onEmoteClick)

    // Texture paints are a URL per layer; animated ones advance on their own frame loop
    val textureUrls =
        remember(namePaint) {
            namePaint
                ?.layers
                .orEmpty()
                .mapNotNull { layer -> (layer.shape as? SevenTVPaintShape.Image)?.url }
                .distinct()
        }
    val textures =
        rememberPaintTextures(urls = textureUrls)

    // Cached offscreen rendering of the painted name (with chained shadows). Animated textures change every
    // frame, so those rows render per pass instead of reusing the cache.
    val paintedNameLayoutState = paintedNameLayout
    val cachedRenderedPaint =
        remember(
            namePaint,
            paintedNameLayoutState.value,
            nameColor,
            density.density,
            layoutDirection,
            textures,
        ) {
            val layout = paintedNameLayoutState.value?.layout ?: return@remember null
            val currentPaint = namePaint ?: return@remember null
            if (currentPaint.shadows.none { it.isVisible }) return@remember null
            val bounds = Rect(Offset.Zero, layout.size.toSize())
            renderPaintedName(currentPaint, layout, bounds, nameColor, textures, density, layoutDirection)
        }

    val measurePolicy =
        remember(text, style, placeholders, maxLines, overflow, textMeasurer, paintedName, nameRange, density) {
            MeasurePolicy { _, constraints ->
                val result =
                    textMeasurer.measure(
                        text = text,
                        style = style,
                        overflow = overflow,
                        maxLines = maxLines,
                        placeholders = placeholders.ranges,
                        constraints = constraints,
                        layoutDirection = layoutDirection,
                        density = this,
                    )
                measuredText.value = MeasuredText(result, placeholders.ids)

                val name = paintedName
                val range = nameRange
                if (name != null && range != null) {
                    val nameLeft = result.getBoundingBox(range.min).left
                    val available = (constraints.maxWidth - nameLeft).toInt().coerceAtLeast(1)
                    val key = PaintedNameKey(name, available, style, density, layoutDirection)
                    if (paintedNameLayout.value?.key != key) {
                        val nameStyle = style.copy(fontWeight = FontWeight.Bold)
                        val measured =
                            textMeasurer.measure(
                                text = AnnotatedString(name),
                                style = nameStyle,
                                maxLines = 1,
                                constraints = Constraints(maxWidth = available),
                                layoutDirection = layoutDirection,
                                density = this,
                            )
                        paintedNameLayout.value = PaintedName(key, measured)
                    }
                }
                layout(constraints.constrainWidth(result.size.width), constraints.constrainHeight(result.size.height)) {}
            }
        }

    Layout(
        measurePolicy = measurePolicy,
        modifier =
            modifier
                .semantics { this.text = text }
                .pointerInput(text, interactionSource) {
                    detectTapGestures(
                        onPress = { offset ->
                            // Emote presses open the emote, the message row shows no press for them
                            val isEmotePress = currentOnEmoteClick.value != null && measuredText.value?.emoteAt(offset, currentInlineImages.value) != null
                            if (interactionSource != null && !isEmotePress) {
                                coroutineScope.launch {
                                    emitPressInteractions(offset, interactionSource, coroutineScope)
                                }
                            }
                        },
                        onTap = { offset ->
                            val measured = measuredText.value ?: return@detectTapGestures
                            val layout = measured.layout
                            val emote = measured.emoteAt(offset, currentInlineImages.value)
                            val onEmote = currentOnEmoteClick.value
                            when {
                                emote != null && onEmote != null -> {
                                    onEmote(emote)
                                }

                                layout.isOnTextLine(offset) -> {
                                    currentOnTextClick.value?.invoke(layout.getOffsetForPosition(offset))
                                }

                                else -> {
                                    currentOnBackgroundClick.value?.invoke()
                                }
                            }
                        },
                        onLongPress = { offset ->
                            val layout = measuredText.value?.layout
                            val textOffset =
                                when {
                                    layout != null && layout.isOnTextLine(offset) -> layout.getOffsetForPosition(offset)
                                    else -> -1
                                }
                            currentOnTextLongClick.value?.invoke(textOffset)
                        },
                    )
                }.drawBehind {
                    val measured = measuredText.value ?: return@drawBehind
                    drawText(measured.layout)
                    inlineImages?.run { drawImages(measured.layout, measured.placeholderIds) }

                    // The paint replaces the nickname: the span is transparent, so this is the only glyph pass.
                    val range = nameRange ?: return@drawBehind
                    val paint = namePaint ?: return@drawBehind
                    if (range.max <= range.min) return@drawBehind
                    val painted = paintedNameLayout.value?.layout ?: return@drawBehind

                    // Align by baseline: the two layouts carry different leading, aligning by box tops drifted.
                    val rowLine = measured.layout.getLineForOffset(range.min)
                    val paintedLine = painted.getLineForOffset(0)
                    val topLeft =
                        Offset(
                            x = measured.layout.getBoundingBox(range.min).left,
                            y = measured.layout.getLineBaseline(rowLine) - painted.getLineBaseline(paintedLine),
                        )

                    // Match background-clip: text, keeping both the glyph mask and its fill in local coordinates.
                    val bounds = Rect(Offset.Zero, painted.size.toSize())
                    val drawPainted: DrawScope.() -> Int = {
                        var drawnLayers = 0
                        paint.layers.forEachIndexed { index, layer ->
                            val texture = (layer.shape as? SevenTVPaintShape.Image)?.let { textures[it.url] }
                            val brush = layer.shape.toBrush(bounds.size, nameColor, texture) ?: return@forEachIndexed

                            drawIntoCanvas { canvas ->
                                canvas.saveLayer(
                                    bounds,
                                    androidx.compose.ui.graphics
                                        .Paint(),
                                )
                            }
                            drawText(painted)
                            drawRect(
                                brush = brush,
                                topLeft = bounds.topLeft,
                                size = bounds.size,
                                alpha = layer.opacity,
                                blendMode = BlendMode.SrcIn,
                            )
                            drawIntoCanvas { it.restore() }
                            drawnLayers++
                        }
                        drawnLayers
                    }

                    val drawnLayers =
                        if (paint.shadows.any { it.isVisible }) {
                            // Shadows chain like chatterino7's drop-shadow filters, so the painted name renders
                            // offscreen first, padded so every blurred copy fits. Animated textures change every
                            // frame, so those rows bypass the cached rendering.
                            val hasAnimatedTexture =
                                paint.layers.any { layer ->
                                    (layer.shape as? SevenTVPaintShape.Image)?.url in textures.animatedUrls
                                }
                            val rendered =
                                when {
                                    hasAnimatedTexture -> renderPaintedName(paint, painted, bounds, nameColor, textures, density, layoutDirection)
                                    cachedRenderedPaint != null -> cachedRenderedPaint
                                    else -> renderPaintedName(paint, painted, bounds, nameColor, textures, density, layoutDirection)
                                }
                            drawImage(
                                rendered.image,
                                dstOffset =
                                    IntOffset(
                                        (topLeft.x - rendered.padX).roundToInt(),
                                        (topLeft.y - rendered.padY).roundToInt(),
                                    ),
                            )
                            rendered.drawnLayers
                        } else {
                            translate(left = topLeft.x, top = topLeft.y) { drawPainted() }
                        }

                    // A texture that has not loaded yet draws nothing, so the nickname needs a fallback
                    if (drawnLayers == 0) {
                        drawText(painted, color = nameColor, topLeft = topLeft)
                    }
                },
    )
}

// Chained drop shadows the way chatterino7 and CSS filter: drop-shadow() render them: every shadow is cast from
// the result of the previous one, and the painted name ends up composited on top of all of them.
internal fun Bitmap.withPaintShadows(
    shadows: List<SevenTVPaintShadowUi>,
    density: Float,
): Bitmap {
    var visible = this
    var mask = extractAlpha()
    shadows.filter { it.isVisible }.forEach { shadow ->
        val dx = shadow.offsetX * density
        val dy = shadow.offsetY * density
        val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = shadow.color
            maskFilter = BlurMaskFilter((shadow.blur * density).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL)
        }
        val next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(next)
        canvas.drawBitmap(mask, dx, dy, shadowPaint)
        canvas.drawBitmap(visible, 0f, 0f, null)
        visible = next

        val nextMask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val maskCanvas = android.graphics.Canvas(nextMask)
        maskCanvas.drawBitmap(mask, 0f, 0f, null)
        maskCanvas.drawBitmap(mask, dx, dy, shadowPaint)
        mask = nextMask
    }
    return visible
}

/** One rendered painted nickname: the final (shadowed) image and where the name sits inside it. */
private class RenderedPaint(
    val image: ImageBitmap,
    val drawnLayers: Int,
    val padX: Float,
    val padY: Float,
)

/**
 * Renders the painted nickname offscreen and applies all shadows, chained. Every shadow is cast from the
 * accumulated result of the previous ones, so padding sums their extents instead of taking the largest one.
 */
private fun renderPaintedName(
    paint: SevenTVPaintUi,
    painted: TextLayoutResult,
    bounds: Rect,
    nameColor: Color,
    textures: PaintTextures,
    density: Density,
    layoutDirection: LayoutDirection,
): RenderedPaint {
    val px = density.density
    val visibleShadows = paint.shadows.filter { it.isVisible }
    val padX = visibleShadows.sumOf { (abs(it.offsetX) + it.blur * 2f).toDouble() }.toFloat() * px + 8f
    val padY = visibleShadows.sumOf { (abs(it.offsetY) + it.blur * 2f).toDouble() }.toFloat() * px + 8f
    val expanded = Size(bounds.width + 2 * padX, bounds.height + 2 * padY)
    val bitmap = Bitmap.createBitmap(
        expanded.width.roundToInt().coerceAtLeast(1),
        expanded.height.roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    var drawnLayers = 0
    CanvasDrawScope().draw(
        density,
        layoutDirection,
        androidx.compose.ui.graphics
            .Canvas(android.graphics.Canvas(bitmap)),
        expanded,
    ) {
        translate(padX, padY) {
            paint.layers.forEach { layer ->
                val texture = (layer.shape as? SevenTVPaintShape.Image)?.let { textures[it.url] }
                val brush = layer.shape.toBrush(bounds.size, nameColor, texture)
                if (brush == null) {
                    return@forEach
                }

                drawIntoCanvas { canvas ->
                    canvas.saveLayer(
                        bounds,
                        androidx.compose.ui.graphics
                            .Paint(),
                    )
                }
                drawText(painted)
                drawRect(
                    brush = brush,
                    topLeft = bounds.topLeft,
                    size = bounds.size,
                    alpha = layer.opacity,
                    blendMode = BlendMode.SrcIn,
                )
                drawIntoCanvas { it.restore() }
                drawnLayers++
            }
        }
    }
    val shadowed = bitmap.withPaintShadows(paint.shadows, px)
    return RenderedPaint(shadowed.asImageBitmap(), drawnLayers, padX, padY)
}

// Placeholder ids are kept with the layout, their order matches its placeholder rects
private class MeasuredText(
    val layout: TextLayoutResult,
    val placeholderIds: List<String>,
)

private fun TextLayoutResult.isOnTextLine(offset: Offset): Boolean {
    val line = getLineForVerticalPosition(offset.y)
    return offset.x in getLineLeft(line)..getLineRight(line)
}

private fun MeasuredText.emoteAt(
    offset: Offset,
    inlineImages: InlineImages?,
): EmoteUi? {
    val index = layout.placeholderRects.indexOfFirst { it?.contains(offset) == true }
    return placeholderIds.getOrNull(index)?.let { inlineImages?.emoteFor(it) }
}

// Delayed like clickable presses in scrollable containers, so swipes and scrolls starting on the text show no ripple
private suspend fun PressGestureScope.emitPressInteractions(
    offset: Offset,
    source: MutableInteractionSource,
    scope: CoroutineScope,
) {
    var press: PressInteraction.Press? = null
    val delayedPress =
        scope.launch {
            delay(ViewConfiguration.getTapTimeout().toLong())
            press = PressInteraction.Press(offset).also { source.emit(it) }
        }
    val isReleased = tryAwaitRelease()
    val emittedPress = press
    when {
        delayedPress.isActive -> {
            delayedPress.cancelAndJoin()
            // A tap shorter than the delay still shows its press
            if (isReleased) {
                val tap = PressInteraction.Press(offset)
                source.emit(tap)
                source.emit(PressInteraction.Release(tap))
            }
        }

        emittedPress != null -> {
            source.emit(
                when {
                    isReleased -> PressInteraction.Release(emittedPress)
                    else -> PressInteraction.Cancel(emittedPress)
                },
            )
        }
    }
}

@Immutable
internal class TextPlaceholders(
    val ranges: List<AnnotatedString.Range<Placeholder>>,
    val ids: List<String>,
)

// Inline content without known dimensions keeps its alternate text, spacers are sized by their id
internal fun buildPlaceholders(
    knownDimensions: ImmutableMap<String, EmoteDimensions>,
    text: AnnotatedString,
    density: Density,
): TextPlaceholders {
    val ranges = mutableListOf<AnnotatedString.Range<Placeholder>>()
    val ids = mutableListOf<String>()
    text.getStringAnnotations(INLINE_CONTENT_TAG, 0, text.length).forEach { annotation ->
        val id = annotation.item
        val dimensions =
            knownDimensions[id] ?: when {
                isSpacerId(id) -> EmoteDimensions(id, with(density) { spacerWidthDp(id).dp.toPx().toInt() }, 1)
                else -> return@forEach
            }
        val placeholder =
            with(density) {
                Placeholder(
                    width = dimensions.widthPx.toDp().toSp(),
                    height = dimensions.heightPx.toDp().toSp(),
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                )
            }
        ranges += AnnotatedString.Range(placeholder, annotation.start, annotation.end)
        ids += id
    }
    return TextPlaceholders(ranges, ids)
}

internal const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"

/** A nickname layout measured for one available width. */
private class PaintedName(
    val key: Any,
    val layout: TextLayoutResult,
)

private data class PaintedNameKey(
    val name: String,
    val availableWidth: Int,
    val style: TextStyle,
    val density: Density,
    val layoutDirection: androidx.compose.ui.unit.LayoutDirection,
)
