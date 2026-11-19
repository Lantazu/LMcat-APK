package com.lmcattabs.desktopcat

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class CoatPalette(
    val id: String,
    val label: String,
    val primary: Int,
    val shadow: Int,
    val accent: Int,
    val belly: Int,
    val outline: Int,
    val earInner: Int,
    val eyeOuter: Int,
    val eyeColor: Int,
    val nose: Int
)

object CatPalettes {
    val ALL: List<CoatPalette> = listOf(
        CoatPalette(
            "pamuk", "Pamuk",
            Color.rgb(248, 252, 255), Color.rgb(206, 226, 250), Color.rgb(158, 197, 242),
            Color.WHITE, Color.rgb(13, 21, 44), Color.rgb(255, 174, 204),
            Color.rgb(13, 46, 130), Color.rgb(36, 120, 255), Color.rgb(255, 93, 143)
        ),
        CoatPalette(
            "marmalade", "Sarı Tekir",
            Color.rgb(255, 170, 85), Color.rgb(225, 124, 46), Color.rgb(176, 80, 22),
            Color.rgb(255, 245, 228), Color.rgb(30, 18, 16), Color.rgb(255, 179, 186),
            Color.rgb(20, 89, 61), Color.rgb(46, 189, 126), Color.rgb(255, 107, 139)
        ),
        CoatPalette(
            "domino", "Smokin",
            Color.rgb(53, 61, 86), Color.rgb(32, 38, 56), Color.rgb(20, 24, 38),
            Color.WHITE, Color.rgb(11, 14, 26), Color.rgb(240, 166, 184),
            Color.rgb(140, 90, 8), Color.rgb(245, 184, 46), Color.rgb(255, 123, 156)
        ),
        CoatPalette(
            "mochi", "Mochi",
            Color.rgb(255, 248, 239), Color.rgb(232, 214, 198), Color.rgb(230, 126, 53),
            Color.rgb(64, 60, 77), Color.rgb(25, 20, 33), Color.rgb(255, 175, 184),
            Color.rgb(27, 99, 64), Color.rgb(72, 184, 120), Color.rgb(255, 110, 144)
        ),
        CoatPalette(
            "siamese", "Siyam",
            Color.rgb(245, 232, 211), Color.rgb(218, 194, 164), Color.rgb(94, 68, 55),
            Color.rgb(255, 250, 240), Color.rgb(28, 19, 20), Color.rgb(168, 131, 116),
            Color.rgb(12, 74, 138), Color.rgb(42, 166, 255), Color.rgb(89, 61, 50)
        ),
        CoatPalette(
            "silver", "Gümüş",
            Color.rgb(200, 214, 232), Color.rgb(154, 174, 200), Color.rgb(98, 117, 145),
            Color.WHITE, Color.rgb(18, 27, 44), Color.rgb(247, 178, 196),
            Color.rgb(13, 99, 82), Color.rgb(45, 212, 159), Color.rgb(240, 125, 155)
        )
    )

    fun byId(id: String): CoatPalette = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

class CatSpriteRenderer {
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        isFakeBoldText = true
    }

    private fun px(
        canvas: Canvas,
        ox: Float,
        oy: Float,
        u: Float,
        facing: Int,
        gx: Float,
        gy: Float,
        gw: Float,
        gh: Float,
        color: Int
    ) {
        paint.color = color
        val left = if (facing >= 0) ox + gx * u else ox - (gx + gw) * u
        val top = oy + gy * u
        canvas.drawRect(left, top, left + gw * u, top + gh * u, paint)
    }

    fun drawCat(
        canvas: Canvas,
        centerX: Float,
        footY: Float,
        dpScale: Float,
        profile: CatProfile,
        action: String,
        facing: Int,
        animTime: Float,
        blinkActive: Boolean,
        climbSide: Int // -1 left wall, 1 right wall, 0 normal
    ) {
        val pal = CatPalettes.byId(profile.coat)
        val u = (2.15f * dpScale * profile.scale).coerceAtLeast(1.8f)

        canvas.save()
        if (action == "CLIMB_WALL" && climbSide != 0) {
            // Rotate sprite neatly onto the left or right phone screen wall
            canvas.translate(centerX, footY - 12f * u)
            canvas.rotate(if (climbSide < 0) 90f else -90f)
            drawBodyAndHead(canvas, 0f, 8f * u, u, if (climbSide < 0) 1 else -1, pal, profile, "RUN", animTime * 1.1f, blinkActive)
            canvas.restore()
            return
        }

        // Subtle shadow when grounded
        if (action != "JUMP" && action != "FALL" && action != "DANGLE") {
            paint.color = Color.argb(65, 10, 14, 28)
            val sw = if (action == "SLEEP_LOAF") 14f * u else 11f * u
            canvas.drawRect(centerX - sw, footY - 1.2f * u, centerX + sw, footY + 1.4f * u, paint)
        }

        drawBodyAndHead(canvas, centerX, footY, u, facing, pal, profile, action, animTime, blinkActive)
        canvas.restore()
    }

    private fun drawBodyAndHead(
        canvas: Canvas,
        ox: Float,
        oy: Float,
        u: Float,
        facing: Int,
        pal: CoatPalette,
        profile: CatProfile,
        action: String,
        t: Float,
        blinkActive: Boolean
    ) {
        val isRun = action == "RUN"
        val isWalk = action == "WALK"
        val isSleep = action == "SLEEP_LOAF"
        val isDangle = action == "DANGLE"
        val isAir = action == "JUMP" || action == "FALL"
        val isStretch = action == "BIG_STRETCH"

        if (isSleep) {
            // Curled sleeping loaf + animated gentle breath
            val breath = sin(t * 2.2f) * 0.45f
            // Tail tucked in front
            px(canvas, ox, oy, u, facing, -12f, -5f, 24f, 5f, pal.outline)
            px(canvas, ox, oy, u, facing, -11f, -10f + breath, 22f, 9f, pal.outline)
            px(canvas, ox, oy, u, facing, -10f, -9f + breath, 20f, 7f, pal.primary)
            px(canvas, ox, oy, u, facing, -10f, -4f, 18f, 3f, pal.shadow)
            // Tucked head
            px(canvas, ox, oy, u, facing, 2f, -14f + breath, 11f, 9f, pal.outline)
            px(canvas, ox, oy, u, facing, 3f, -13f + breath, 9f, 7f, pal.primary)
            // Ears
            px(canvas, ox, oy, u, facing, 3f, -16f + breath, 3f, 3f, pal.outline)
            px(canvas, ox, oy, u, facing, 9f, -16f + breath, 3f, 3f, pal.outline)
            px(canvas, ox, oy, u, facing, 4f, -15f + breath, 1.5f, 2f, pal.earInner)
            px(canvas, ox, oy, u, facing, 9.5f, -15f + breath, 1.5f, 2f, pal.earInner)
            // Peaceful closed eyes
            px(canvas, ox, oy, u, facing, 5f, -9.5f + breath, 2.2f, 1f, pal.outline)
            px(canvas, ox, oy, u, facing, 9f, -9.5f + breath, 2.2f, 1f, pal.outline)
            px(canvas, ox, oy, u, facing, 7.2f, -8.3f + breath, 1.4f, 1f, pal.nose)
            drawHeadwear(canvas, ox, oy, u, facing, 3.5f, -16.5f + breath, profile.headwear)
            return
        }

        // Agile 3-segment rotary gallop spine kinematics
        val gallopPhase = t * 14.5f
        val walkPhase = t * 8.2f
        val spineFlex = if (isRun) sin(gallopPhase) else 0f
        val spineStretch = if (isRun) cos(gallopPhase) * 1.8f else if (isStretch) 3.5f else 0f
        val bodyLift = if (isRun) (-1.8f + (-sin(gallopPhase + 0.6f)).coerceAtLeast(0f) * -2.2f) else if (isWalk) -absSin(walkPhase) * 0.9f else 0f
        val dangleDrop = if (isDangle) 3.5f else 0f

        // Whip counterbalance tail
        val tailWag = if (isRun) sin(gallopPhase - 1.1f) * 3.0f else sin(t * 3.8f) * 2.2f
        val tailBaseX = -10f - spineStretch * 0.5f
        val tailBaseY = -10f + bodyLift
        px(canvas, ox, oy, u, facing, tailBaseX - 5f, tailBaseY - 4f + tailWag, 6f, 3f, pal.outline)
        px(canvas, ox, oy, u, facing, tailBaseX - 4f, tailBaseY - 3f + tailWag, 5f, 1.8f, pal.primary)
        px(canvas, ox, oy, u, facing, tailBaseX - 8f, tailBaseY - 6f + tailWag * 1.2f, 4f, 3f, pal.outline)
        px(canvas, ox, oy, u, facing, tailBaseX - 7f, tailBaseY - 5f + tailWag * 1.2f, 2.6f, 1.6f, pal.accent)

        // Back legs (far side)
        val backStride1 = if (isRun) sin(gallopPhase + 2.5f) * 5.2f else if (isWalk) sin(walkPhase + PI.toFloat()) * 3.0f else if (isAir) -3.5f else 0f
        val backLift1 = if (isRun) (-cos(gallopPhase + 2.5f)).coerceAtLeast(0f) * 3.2f else if (isWalk) (-cos(walkPhase + PI.toFloat())).coerceAtLeast(0f) * 1.6f else if (isAir) 2.2f else 0f
        val frontStride1 = if (isRun) sin(gallopPhase + 0.4f) * 5.6f else if (isWalk) sin(walkPhase) * 3.0f else if (isAir) 3.8f else 0f
        val frontLift1 = if (isRun) (-cos(gallopPhase + 0.4f)).coerceAtLeast(0f) * 3.4f else if (isWalk) (-cos(walkPhase)).coerceAtLeast(0f) * 1.6f else if (isAir) 2.6f else 0f

        drawLeg(canvas, ox, oy, u, facing, -7.5f + backStride1 * 0.8f, -5.5f + bodyLift - backLift1 + dangleDrop, pal.shadow, pal.outline)
        drawLeg(canvas, ox, oy, u, facing, 3.0f + frontStride1 * 0.8f, -5.5f + bodyLift - frontLift1 + dangleDrop, pal.shadow, pal.outline)

        // 3-segment flexible spine torso
        val hipY = -12f + bodyLift + spineFlex * 1.1f
        val midY = -12.5f + bodyLift - spineFlex * 0.8f
        val shY = -12.2f + bodyLift - spineFlex * 0.9f

        // Outline torso
        px(canvas, ox, oy, u, facing, -10f - spineStretch * 0.4f, hipY - 1f, 8.5f, 9f + dangleDrop * 0.4f, pal.outline)
        px(canvas, ox, oy, u, facing, -3.5f, midY - 1f, 8.5f, 9f + dangleDrop * 0.4f, pal.outline)
        px(canvas, ox, oy, u, facing, 2.5f + spineStretch * 0.4f, shY - 1f, 8f, 9f + dangleDrop * 0.4f, pal.outline)

        // Primary fur segments
        px(canvas, ox, oy, u, facing, -9f - spineStretch * 0.4f, hipY, 7f, 7f, pal.primary)
        px(canvas, ox, oy, u, facing, -3f, midY, 7.5f, 7f, pal.primary)
        px(canvas, ox, oy, u, facing, 2.5f + spineStretch * 0.4f, shY, 6.8f, 7f, pal.primary)

        // Belly + cel shading
        px(canvas, ox, oy, u, facing, -7f, midY + 4.8f, 13f, 2.2f, pal.belly)
        px(canvas, ox, oy, u, facing, -8f - spineStretch * 0.3f, hipY + 1f, 3.2f, 2.5f, pal.accent)

        // Near-side foreground legs (for clear 4-paw readability)
        val backStride2 = if (isRun) sin(gallopPhase + 3.1f) * 5.6f else if (isWalk) sin(walkPhase + 0.5f) * 3.0f else if (isAir) -2.2f else 0f
        val backLift2 = if (isRun) (-cos(gallopPhase + 3.1f)).coerceAtLeast(0f) * 3.2f else if (isWalk) (-cos(walkPhase + 0.5f)).coerceAtLeast(0f) * 1.5f else if (isAir) 1.5f else 0f
        val frontStride2 = if (isRun) sin(gallopPhase + 1.0f) * 5.8f else if (isWalk) sin(walkPhase + PI.toFloat() + 0.4f) * 3.0f else if (isAir) 4.4f else 0f
        val frontLift2 = if (isRun) (-cos(gallopPhase + 1.0f)).coerceAtLeast(0f) * 3.4f else if (isWalk) (-cos(walkPhase + PI.toFloat() + 0.4f)).coerceAtLeast(0f) * 1.5f else if (isAir) 2.2f else 0f

        drawLeg(canvas, ox, oy, u, facing, -6.2f + backStride2, -5.2f + bodyLift - backLift2 + dangleDrop, pal.primary, pal.outline)
        drawLeg(canvas, ox, oy, u, facing, 4.4f + frontStride2, -5.2f + bodyLift - frontLift2 + dangleDrop, pal.primary, pal.outline)

        // Collar
        if (profile.collar != "none") {
            val cCol = when (profile.collar) {
                "bell_red" -> Color.rgb(239, 68, 68)
                "star_teal" -> Color.rgb(20, 184, 166)
                else -> Color.rgb(217, 119, 6)
            }
            px(canvas, ox, oy, u, facing, 3.5f + spineStretch * 0.35f, shY + 1.8f, 6.2f, 1.8f, cCol)
            px(canvas, ox, oy, u, facing, 7.6f + spineStretch * 0.35f, shY + 3.4f, 1.8f, 1.8f, Color.rgb(251, 191, 36))
        }

        // Stabilized Head
        val headX = 3.0f + spineStretch * 0.42f
        val headY = -22.5f + bodyLift * 0.45f + sin(t * 3.1f) * 0.35f

        // Ears
        px(canvas, ox, oy, u, facing, headX + 0.5f, headY - 3.5f, 3.6f, 4f, pal.outline)
        px(canvas, ox, oy, u, facing, headX + 8.0f, headY - 3.5f, 3.6f, 4f, pal.outline)
        px(canvas, ox, oy, u, facing, headX + 1.4f, headY - 2.4f, 2f, 2.8f, pal.primary)
        px(canvas, ox, oy, u, facing, headX + 8.8f, headY - 2.4f, 2f, 2.8f, pal.primary)
        px(canvas, ox, oy, u, facing, headX + 1.9f, headY - 1.6f, 1.2f, 1.8f, pal.earInner)
        px(canvas, ox, oy, u, facing, headX + 9.2f, headY - 1.6f, 1.2f, 1.8f, pal.earInner)

        // Head shape
        px(canvas, ox, oy, u, facing, headX - 0.5f, headY, 13f, 11f, pal.outline)
        px(canvas, ox, oy, u, facing, headX + 0.5f, headY + 1f, 11f, 9f, pal.primary)
        px(canvas, ox, oy, u, facing, headX + 0.5f, headY + 8.2f, 11f, 1.8f, pal.shadow)

        // Eyes
        if (blinkActive || action == "HAPPY_PURR") {
            px(canvas, ox, oy, u, facing, headX + 2.4f, headY + 5.2f, 2.5f, 1.1f, pal.outline)
            px(canvas, ox, oy, u, facing, headX + 7.6f, headY + 5.2f, 2.5f, 1.1f, pal.outline)
        } else {
            px(canvas, ox, oy, u, facing, headX + 2.1f, headY + 3.4f, 2.9f, 3.5f, pal.eyeOuter)
            px(canvas, ox, oy, u, facing, headX + 7.4f, headY + 3.4f, 2.9f, 3.5f, pal.eyeOuter)
            px(canvas, ox, oy, u, facing, headX + 2.5f, headY + 4.3f, 2.1f, 2.3f, pal.eyeColor)
            px(canvas, ox, oy, u, facing, headX + 7.8f, headY + 4.3f, 2.1f, 2.3f, pal.eyeColor)
            // Glossy catchlights
            px(canvas, ox, oy, u, facing, headX + 2.4f, headY + 3.8f, 1.1f, 1.1f, Color.WHITE)
            px(canvas, ox, oy, u, facing, headX + 7.7f, headY + 3.8f, 1.1f, 1.1f, Color.WHITE)
        }

        // Cute pink nose & blush
        px(canvas, ox, oy, u, facing, headX + 5.5f, headY + 6.9f, 1.6f, 1.2f, pal.nose)
        px(canvas, ox, oy, u, facing, headX + 1.2f, headY + 7.0f, 1.5f, 1.0f, Color.argb(145, 255, 110, 155))
        px(canvas, ox, oy, u, facing, headX + 9.6f, headY + 7.0f, 1.5f, 1.0f, Color.argb(145, 255, 110, 155))

        drawEyewear(canvas, ox, oy, u, facing, headX, headY, profile.eyewear)
        drawHeadwear(canvas, ox, oy, u, facing, headX + 2.8f, headY - 3.6f, profile.headwear)
    }

    private fun drawLeg(
        canvas: Canvas,
        ox: Float,
        oy: Float,
        u: Float,
        facing: Int,
        gx: Float,
        gy: Float,
        fill: Int,
        outline: Int
    ) {
        px(canvas, ox, oy, u, facing, gx - 0.6f, gy - 0.6f, 3.6f, 6.2f, outline)
        px(canvas, ox, oy, u, facing, gx, gy, 2.4f, 5.0f, fill)
    }

    private fun drawHeadwear(
        canvas: Canvas,
        ox: Float,
        oy: Float,
        u: Float,
        facing: Int,
        hx: Float,
        hy: Float,
        headwear: String
    ) {
        when (headwear) {
            "crown" -> {
                px(canvas, ox, oy, u, facing, hx - 0.5f, hy - 2.6f, 7.2f, 3.4f, Color.rgb(13, 21, 44))
                px(canvas, ox, oy, u, facing, hx, hy - 1.8f, 6.2f, 2.2f, Color.rgb(245, 184, 46))
                px(canvas, ox, oy, u, facing, hx + 0.3f, hy - 3.4f, 1.4f, 1.8f, Color.rgb(255, 240, 138))
                px(canvas, ox, oy, u, facing, hx + 2.4f, hy - 4.0f, 1.5f, 2.4f, Color.rgb(255, 240, 138))
                px(canvas, ox, oy, u, facing, hx + 4.5f, hy - 3.4f, 1.4f, 1.8f, Color.rgb(255, 240, 138))
                px(canvas, ox, oy, u, facing, hx + 2.5f, hy - 1.3f, 1.3f, 1.3f, Color.rgb(244, 63, 94))
            }
            "ribbon" -> {
                px(canvas, ox, oy, u, facing, hx, hy - 1.5f, 6.2f, 2.5f, Color.rgb(244, 63, 94))
                px(canvas, ox, oy, u, facing, hx + 2.3f, hy - 1.2f, 1.6f, 1.8f, Color.rgb(255, 182, 198))
            }
            "beanie" -> {
                px(canvas, ox, oy, u, facing, hx - 1.2f, hy - 2.5f, 8.6f, 3.5f, Color.rgb(56, 189, 248))
                px(canvas, ox, oy, u, facing, hx + 2.2f, hy - 4.2f, 2.0f, 2.0f, Color.WHITE)
            }
            "wizard" -> {
                px(canvas, ox, oy, u, facing, hx - 2.0f, hy - 0.8f, 10.2f, 1.8f, Color.rgb(99, 102, 241))
                px(canvas, ox, oy, u, facing, hx + 0.4f, hy - 5.2f, 5.4f, 4.6f, Color.rgb(79, 70, 229))
                px(canvas, ox, oy, u, facing, hx + 2.4f, hy - 3.0f, 1.4f, 1.4f, Color.rgb(251, 191, 36))
            }
        }
    }

    private fun drawEyewear(
        canvas: Canvas,
        ox: Float,
        oy: Float,
        u: Float,
        facing: Int,
        hx: Float,
        hy: Float,
        eyewear: String
    ) {
        when (eyewear) {
            "shades" -> {
                px(canvas, ox, oy, u, facing, hx + 1.5f, hy + 3.4f, 10.0f, 3.0f, Color.rgb(15, 23, 42))
                px(canvas, ox, oy, u, facing, hx + 2.1f, hy + 3.9f, 2.2f, 1.0f, Color.rgb(56, 189, 248))
                px(canvas, ox, oy, u, facing, hx + 7.5f, hy + 3.9f, 2.2f, 1.0f, Color.rgb(56, 189, 248))
            }
            "round" -> {
                px(canvas, ox, oy, u, facing, hx + 1.6f, hy + 3.2f, 3.8f, 3.8f, Color.argb(110, 251, 191, 36))
                px(canvas, ox, oy, u, facing, hx + 6.9f, hy + 3.2f, 3.8f, 3.8f, Color.argb(110, 251, 191, 36))
            }
            "heart" -> {
                px(canvas, ox, oy, u, facing, hx + 1.8f, hy + 3.4f, 3.4f, 3.2f, Color.rgb(244, 63, 94))
                px(canvas, ox, oy, u, facing, hx + 7.1f, hy + 3.4f, 3.4f, 3.2f, Color.rgb(244, 63, 94))
            }
        }
    }

    fun drawPillow(canvas: Canvas, x: Float, y: Float, dpScale: Float) {
        val w = 44f * dpScale
        val h = 13f * dpScale
        paint.color = Color.rgb(13, 21, 44)
        canvas.drawRect(x - w - 3f, y - h - 3f, x + w + 3f, y + 3f, paint)
        paint.color = Color.rgb(99, 102, 241)
        canvas.drawRect(x - w, y - h, x + w, y, paint)
        paint.color = Color.rgb(165, 180, 252)
        canvas.drawRect(x - w + 6f, y - h + 3f, x + w - 6f, y - h * 0.45f, paint)
    }

    fun drawFish(canvas: Canvas, x: Float, y: Float, dpScale: Float) {
        val u = 2.4f * dpScale
        paint.color = Color.rgb(13, 21, 44)
        canvas.drawRect(x - 6f * u, y - 3f * u, x + 5f * u, y + 3f * u, paint)
        paint.color = Color.rgb(56, 189, 248)
        canvas.drawRect(x - 5f * u, y - 2f * u, x + 4f * u, y + 2f * u, paint)
        paint.color = Color.rgb(186, 230, 253)
        canvas.drawRect(x - 3f * u, y - 1f * u, x + 2f * u, y + 1f * u, paint)
    }

    fun drawYarn(canvas: Canvas, x: Float, y: Float, dpScale: Float) {
        val r = 13f * dpScale
        paint.color = Color.rgb(13, 21, 44)
        canvas.drawCircle(x, y, r + 2.5f, paint)
        paint.color = Color.rgb(244, 63, 94)
        canvas.drawCircle(x, y, r, paint)
        paint.color = Color.rgb(253, 164, 175)
        canvas.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.35f, paint)
    }

    fun drawParticle(canvas: Canvas, p: OverlayParticle, dpScale: Float) {
        val alpha = ((p.life / p.maxLife).coerceIn(0f, 1f) * 255f).roundToInt()
        textPaint.color = Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color))
        textPaint.textSize = (13f * dpScale * p.scale).coerceAtLeast(18f)
        val label = when (p.kind) {
            "zzz" -> "Z"
            "heart" -> "♥"
            "sparkle" -> "✦"
            else -> "•"
        }
        canvas.drawText(label, p.x, p.y, textPaint)
    }

    private fun absSin(v: Float): Float {
        val s = sin(v)
        return if (s < 0f) -s else s
    }
}
