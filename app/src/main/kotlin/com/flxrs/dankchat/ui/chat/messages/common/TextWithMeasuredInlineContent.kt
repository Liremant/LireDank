package com.flxrs.dankchat.ui.chat.messages.common

import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.compose.LocalPlatformContext
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.data.repo.emote.SevenTVPaintUi
import com.flxrs.dankchat.ui.chat.messages.toBrush
import com.flxrs.dankchat.ui.chat.EmoteUi
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    nameShadow: Shadow? = null,
    onTextClick: ((Int) -> Unit)? = null,
    onBackgroundClick: (() -> Unit)? = null,
    onTextLongClick: ((Int) -> Unit)? = null,
    onEmoteClick: ((EmoteUi) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val density = LocalDensity.current
    val placeholders = remember(knownDimensions, text, density) { buildPlaceholders(knownDimensions, text, density) }
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val coroutineScope = rememberCoroutineScope()

    // A standalone layout of just the nickname, so the paint shader covers the name and nothing else
    val paintedNameLayout =
        remember(namePaint, nameRange, text, style, textMeasurer) {
            val range = nameRange
            if (namePaint == null || range == null || range.max <= range.min) {
                null
            } else {
                val name = text.text.substring(range.min, range.max)
                // The nickname span is bold, so the standalone layout has to be bold too or the painted glyphs
                // would be narrower than the ones underneath and drift out of alignment
                textMeasurer.measure(AnnotatedString(name), style = style.copy(fontWeight = FontWeight.Bold))
            }
        }
    val measuredText = remember { mutableStateOf<MeasuredText?>(null) }
    val currentInlineImages = rememberUpdatedState(inlineImages)
    val currentOnTextClick = rememberUpdatedState(onTextClick)
    val currentOnBackgroundClick = rememberUpdatedState(onBackgroundClick)
    val currentOnTextLongClick = rememberUpdatedState(onTextLongClick)
    val currentOnEmoteClick = rememberUpdatedState(onEmoteClick)

    // Texture paints are a URL per layer; they are loaded once and handed to the shader as a bitmap
    val textureUrls =
        remember(namePaint) {
            namePaint?.layers.orEmpty().mapNotNull { layer -> (layer.shape as? SevenTVPaintShape.Image)?.url }.distinct()
        }
    val textures = remember(textureUrls) { mutableStateMapOf<String, ImageBitmap>() }
    val platformContext = LocalPlatformContext.current
    LaunchedEffect(textureUrls) {
        textureUrls.forEach { url ->
            if (textures.containsKey(url)) return@forEach
            runCatching { platformContext.imageLoader.execute(ImageRequest.Builder(platformContext).data(url).build()) }
                .getOrNull()
                ?.let { result -> (result as? SuccessResult)?.image?.asDrawable(platformContext.resources)?.toBitmap()?.asImageBitmap() }
                ?.let { bitmap -> textures[url] = bitmap }
        }
    }

    val measurePolicy =
        remember(text, style, placeholders, maxLines, overflow, textMeasurer) {
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

                    // The paint replaces the nickname: the span is transparent, so the only glyph pass for the
                    // name is this one, and it is aligned by baseline - bounding boxes carry different
                    // leading for the two layouts, which is what made the paint drift.
                    val range = nameRange ?: return@drawBehind
                    val paint = namePaint ?: return@drawBehind
                    if (range.max <= range.min) return@drawBehind
                    val painted = paintedNameLayout ?: return@drawBehind
                    val line = measured.layout.getLineForOffset(range.min)
                    val paintedLine = painted.getLineForOffset(0)
                    val topLeft =
                        Offset(
                            x = measured.layout.getBoundingBox(range.min).left,
                            y = measured.layout.getLineBaseline(line) - painted.getLineBaseline(paintedLine),
                        )
                    val size = Size(painted.size.width.toFloat(), painted.size.height.toFloat())
                    // Layers stack the way the site stacks backgrounds; shadows belong to the first layer only
                    var drawnLayers = 0
                    paint.layers.forEachIndexed { index, layer ->
                        val texture = (layer.shape as? SevenTVPaintShape.Image)?.let { textures[it.url] }
                        val brush = layer.shape.toBrush(size, nameColor, texture) ?: return@forEachIndexed
                        drawText(
                            textLayoutResult = painted,
                            brush = brush,
                            topLeft = topLeft,
                            alpha = layer.opacity,
                            shadow = if (index == 0) nameShadow else null,
                        )
                        drawnLayers++
                    }
                    // A texture that has not loaded yet draws nothing, so the transparent span needs a fallback
                    // or the nickname would be invisible for a frame
                    if (drawnLayers == 0) {
                        drawText(painted, color = nameColor, topLeft = topLeft)
                    }
                },
    )
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
