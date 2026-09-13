package com.thyids.free_music.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost

/**
 * Shared geometry so every pane in the app feels like one family of objects.
 */
object GlassShapes {
    val Panel: Shape = RoundedCornerShape(28.dp)
    val Card: Shape = RoundedCornerShape(24.dp)
    val Tile: Shape = RoundedCornerShape(18.dp)
    val Pill: Shape = RoundedCornerShape(percent = 50)
    val Circle: Shape = RoundedCornerShape(percent = 50)
}

/**
 * The colours the page is painted with. Panes re-sample this exact palette, so
 * what shows through a pane always matches what surrounds it.
 */
data class BackdropPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val baseTop: Color,
    val baseBottom: Color
)

@Composable
fun rememberBackdropPalette(): BackdropPalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme.primary, scheme.secondary, scheme.tertiary) {
        BackdropPalette(
            primary = scheme.primary,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            baseTop = Color(0xFFFDFCFF),
            baseBottom = Color(0xFFF6F3FF)
        )
    }
}

/**
 * Paints the page background. Panes call this again, offset into their own
 * coordinate space, which is what makes them look like they are sitting on the
 * page rather than painted over it.
 */
fun DrawScope.drawAppBackdrop(
    palette: BackdropPalette,
    viewport: Size,
    drift: Float = 0.5f
) {
    if (viewport.width <= 0f || viewport.height <= 0f) return

    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(palette.baseTop, palette.baseBottom),
            start = Offset.Zero,
            end = Offset(viewport.width, viewport.height)
        )
    )

    fun blob(color: Color, alpha: Float, centerX: Float, centerY: Float, radiusFraction: Float) {
        val center = Offset(viewport.width * centerX, viewport.height * centerY)
        val radius = viewport.width * radiusFraction
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha),
                    color.copy(alpha = alpha * 0.35f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )
    }

    // Colour fields wide enough that a pane always has something to refract.
    blob(palette.primary, 0.46f, 0.12f + 0.14f * drift, 0.10f + 0.05f * drift, 0.78f)
    blob(palette.tertiary, 0.40f, 0.94f - 0.14f * drift, 0.36f + 0.08f * drift, 0.64f)
    blob(palette.secondary, 0.34f, 0.34f - 0.12f * drift, 0.90f - 0.08f * drift, 0.74f)
    blob(Color.White, 0.50f, 0.58f + 0.08f * drift, 0.30f, 0.52f)
}

/** Shared viewport size, so a pane knows where it sits on the page. */
class BackdropViewport {
    var size by mutableStateOf(Size.Zero)
}

val LocalBackdropViewport = staticCompositionLocalOf { BackdropViewport() }

@Composable
fun ProvideBackdropViewport(content: @Composable () -> Unit) {
    val viewport = remember { BackdropViewport() }
    CompositionLocalProvider(LocalBackdropViewport provides viewport, content = content)
}

private val ShadowTint = Color(0x2A241546)

/**
 * Liquid glass, built from four layers, in this order:
 *
 *  1. a live sample of the backdrop behind the pane (optionally blurred),
 *  2. a translucent white tint that lifts and cools it, the way glass does,
 *  3. a gloss along the top edge and a diagonal reflection band,
 *  4. a refraction hairline plus a specular rim that is brightest top-left.
 *
 * Frosted glass stops at step 1; the rim and the refraction line are what make
 * the pane read as a solid piece of glass.
 */
@Composable
fun GlassPane(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.Card,
    fillAlpha: Float = 0.30f,
    shadowElevation: Dp = 10.dp,
    rimAlpha: Float = 0.95f,
    sheenAlpha: Float = 0.22f,
    backdropBlur: Dp = 0.dp,
    pressedScale: Float = 0.97f,
    contentAlignment: Alignment = Alignment.TopStart,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = rememberBackdropPalette()
    val viewport = LocalBackdropViewport.current
    val interactionSource = remember { MutableInteractionSource() }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressScale(interactionSource, pressedScale) else Modifier)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = ShadowTint,
                spotColor = ShadowTint
            )
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = contentAlignment
    ) {
        // 1. Backdrop sample, aligned to the page and clipped to the pane.
        // matchParentSize (not fillMaxSize) matters: fillMaxSize would ask for
        // the parent's maximum height and stretch the pane to fill the screen.
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (backdropBlur > 0.dp) {
                        Modifier.blur(backdropBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    } else Modifier
                )
        ) {
            val sizeOnPage = viewport.size
            if (sizeOnPage.width > 0f) {
                translate(left = -origin.x, top = -origin.y) {
                    drawAppBackdrop(palette, sizeOnPage)
                }
            }
        }
        // 2 & 3. Tint, gloss, reflection.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawGlassBody(fillAlpha, sheenAlpha)
        }
        content()
        // Edges are their own child so they always land on top of the blurred
        // sample layer, which otherwise composites above anything drawn by a
        // parent modifier.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawGlassEdges(shape, rimAlpha, this)
        }
    }
}

private fun DrawScope.drawGlassEdges(shape: Shape, rimAlpha: Float, density: Density) {
    val radius = cornerRadiusPx(shape, size, density)
    val rimInset = 1.dp.toPx()
    val hairInset = 2.6.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = rimAlpha),
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = rimAlpha * 0.9f)
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        topLeft = Offset(rimInset, rimInset),
        size = Size(size.width - rimInset * 2f, size.height - rimInset * 2f),
        cornerRadius = CornerRadius(
            (radius - rimInset).coerceAtLeast(0f),
            (radius - rimInset).coerceAtLeast(0f)
        ),
        style = Stroke(width = 2.dp.toPx())
    )
    drawRoundRect(
        color = Color(0x2E000000),
        topLeft = Offset(hairInset, hairInset),
        size = Size(size.width - hairInset * 2f, size.height - hairInset * 2f),
        cornerRadius = CornerRadius(
            (radius - hairInset).coerceAtLeast(0f),
            (radius - hairInset).coerceAtLeast(0f)
        ),
        style = Stroke(width = 1.2.dp.toPx())
    )
}

/** Shadow, clip and specular rim. The rim is drawn after the content. */
fun Modifier.glassRim(
    shape: Shape,
    shadowElevation: Dp,
    rimAlpha: Float
): Modifier = this
    .shadow(
        elevation = shadowElevation,
        shape = shape,
        clip = false,
        ambientColor = ShadowTint,
        spotColor = ShadowTint
    )
    .clip(shape)
    .drawWithCache {
        val radius = cornerRadiusPx(shape, size, this)
        val corner = CornerRadius(radius, radius)
        val hairline = Stroke(width = 1.2.dp.toPx())
        val rim = Stroke(width = 2.dp.toPx())
        val specular = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = rimAlpha),
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.26f),
                Color.White.copy(alpha = rimAlpha * 0.9f)
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
        onDrawWithContent {
            drawContent()
            // Both strokes are inset so they render whole; a stroke centred on
            // the boundary loses half its width to the clip.
            val rimInset = 1.dp.toPx()
            val hairInset = 2.6.dp.toPx()
            drawRoundRect(
                brush = specular,
                topLeft = Offset(rimInset, rimInset),
                size = Size(size.width - rimInset * 2f, size.height - rimInset * 2f),
                cornerRadius = CornerRadius(
                    (radius - rimInset).coerceAtLeast(0f),
                    (radius - rimInset).coerceAtLeast(0f)
                ),
                style = rim
            )
            drawRoundRect(
                color = Color(0x2E000000),
                topLeft = Offset(hairInset, hairInset),
                size = Size(size.width - hairInset * 2f, size.height - hairInset * 2f),
                cornerRadius = CornerRadius(
                    (radius - hairInset).coerceAtLeast(0f),
                    (radius - hairInset).coerceAtLeast(0f)
                ),
                style = hairline
            )
        }
    }

/** Translucent tint plus the gloss and reflection bands. */
fun DrawScope.drawGlassBody(fillAlpha: Float, sheenAlpha: Float) {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (fillAlpha + 0.22f).fastCoerceAtMost(1f)),
                Color.White.copy(alpha = fillAlpha * 0.82f),
                Color.White.copy(alpha = (fillAlpha * 1.08f).fastCoerceAtMost(1f))
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.10f),
                Color.Transparent
            ),
            startY = 0f,
            endY = size.height * 0.45f
        )
    )
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = sheenAlpha),
                Color.Transparent
            ),
            start = Offset(size.width * 0.05f, 0f),
            end = Offset(size.width * 0.70f, size.height)
        )
    )
}

/**
 * Self-contained glass fill, for small elements that have nothing meaningful
 * behind them at their own scale.
 */
fun Modifier.liquidGlass(
    shape: Shape = GlassShapes.Card,
    fillAlpha: Float = 0.42f,
    shadowElevation: Dp = 10.dp,
    rimAlpha: Float = 0.95f,
    sheenAlpha: Float = 0.22f
): Modifier = this
    .glassRim(shape, shadowElevation, rimAlpha)
    .drawWithCache {
        onDrawWithContent {
            drawGlassBody(fillAlpha, sheenAlpha)
            drawContent()
        }
    }

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private fun cornerRadiusPx(
    shape: Shape,
    size: Size,
    density: androidx.compose.ui.unit.Density
): Float = when (shape) {
    is RoundedCornerShape -> shape.topStart.toPx(size, density)
    else -> 0f
}
